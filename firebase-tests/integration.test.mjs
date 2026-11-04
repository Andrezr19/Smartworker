import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, updateDoc, collection, query, where, getDocs, serverTimestamp } from 'firebase/firestore';
import { ref, set, get, serverTimestamp as realtimeTimestamp } from 'firebase/database';

// Solo emuladores: nunca se ejecutan estas pruebas contra smartworker-bb004.
const projectId = 'demo-smartworker';
const env = await initializeTestEnvironment({
  projectId,
  firestore: { host: '127.0.0.1', port: 8080, rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
  database: { host: '127.0.0.1', port: 9000, rules: await readFile(new URL('../database.rules.json', import.meta.url), 'utf8') }
});
let passed = 0;
async function check(name, fn) { await fn(); console.log(`OK ${++passed}: ${name}`); }
const worker = env.authenticatedContext('worker', {email:'worker@example.com'});
const outsider = env.authenticatedContext('outsider', {email:'outsider@example.com'});
const supervisor = env.authenticatedContext('supervisor', {email:'supervisor@example.com'});
const anonymous = env.unauthenticatedContext();
const profile = {
  uid:'worker', rut:'11111111-1', nombre:'Trabajador de prueba', correo:'worker@example.com',
  rol:'TRABAJADOR', supervisorUid:'', zona:'Sin asignar', cargo:'Trabajador', creadoEn:serverTimestamp()
};
const reading = {
  temperaturaAmbiente:35, temperaturaEstimada:38, humedad:31, uv:9, actividad:'MODERADA',
  exposicionMin:138, aSalvo:false, riesgo:'ALTO', origen:'SIMULADO', lecturaId:'test-reading'
};
try {
  await env.clearFirestore(); await env.clearDatabase();
  await check('Authentication: registro, inicio correcto y contraseña incorrecta', async () => {
    const endpoint = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:';
    const credentials = {email:'firebase-test@example.com',password:'TestOnly1234!',returnSecureToken:true};
    const call = (method, body) => fetch(endpoint+method+'?key=fake-emulator-key', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
    const signup = await call('signUp',credentials); assert.equal(signup.status,200);
    const account = await signup.json(); assert.ok(account.localId);
    const signin = await call('signInWithPassword',credentials); assert.equal(signin.status,200);
    assert.equal((await signin.json()).localId,account.localId);
    assert.equal((await call('signInWithPassword',{...credentials,password:'wrong'})).status,400);
  });
  await check('No se puede registrar un perfil supervisor desde el cliente', () =>
    assertFails(setDoc(doc(worker.firestore(),'usuarios/worker'), {...profile,rol:'SUPERVISOR'})));
  await check('El trabajador crea y lee su perfil sin contraseña', async () => {
    await assertSucceeds(setDoc(doc(worker.firestore(),'usuarios/worker'),profile));
    const result = await assertSucceeds(getDoc(doc(worker.firestore(),'usuarios/worker')));
    assert.equal(result.data().rol,'TRABAJADOR'); assert.equal('password' in result.data(),false);
  });
  await check('No se puede cambiar el rol ni asignarse un supervisor desde el cliente', async () => {
    await assertFails(updateDoc(doc(worker.firestore(),'usuarios/worker'),{rol:'SUPERVISOR'}));
    await assertFails(updateDoc(doc(worker.firestore(),'usuarios/worker'),{supervisorUid:'outsider'}));
  });
  await check('Los perfiles no son públicos ni accesibles por otro trabajador', async () => {
    await assertFails(getDoc(doc(anonymous.firestore(),'usuarios/worker')));
    await assertFails(getDoc(doc(outsider.firestore(),'usuarios/worker')));
  });
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(),'usuarios/supervisor'),{uid:'supervisor',rol:'SUPERVISOR'});
    await updateDoc(doc(ctx.firestore(),'usuarios/worker'),{supervisorUid:'supervisor'});
  });
  await check('El supervisor consulta solo los perfiles asignados', async () => {
    const q = query(collection(supervisor.firestore(),'usuarios'),where('supervisorUid','==','supervisor'),where('rol','==','TRABAJADOR'));
    assert.equal((await assertSucceeds(getDocs(q))).size,1);
    await assertFails(getDocs(collection(supervisor.firestore(),'usuarios')));
  });
  await check('Firestore guarda historial y permite su consulta al supervisor asignado', async () => {
    await assertSucceeds(setDoc(doc(worker.firestore(),'usuarios/worker/historial/test-reading'),{...reading,fecha:serverTimestamp()}));
    const saved = await assertSucceeds(getDoc(doc(supervisor.firestore(),'usuarios/worker/historial/test-reading')));
    assert.equal(saved.data().humedad,31); assert.ok(saved.data().fecha);
    await assertFails(getDoc(doc(outsider.firestore(),'usuarios/worker/historial/test-reading')));
  });
  await check('Historial: no admite contraseñas ni modificaciones posteriores', async () => {
    await assertFails(setDoc(doc(worker.firestore(),'usuarios/worker/historial/other'),{...reading,lecturaId:'other',password:'forbidden',fecha:serverTimestamp()}));
    await assertFails(updateDoc(doc(worker.firestore(),'usuarios/worker/historial/test-reading'),{humedad:99}));
  });
  await check('Realtime Database guarda y devuelve la última lectura del trabajador', async () => {
    await assertSucceeds(set(ref(worker.database(),'lecturasActuales/worker'),{...reading,actualizadoEn:realtimeTimestamp()}));
    const saved = await assertSucceeds(get(ref(worker.database(),'lecturasActuales/worker')));
    assert.equal(saved.val().humedad,31); assert.ok(saved.val().actualizadoEn>0);
  });
  await check('Realtime Database: sin asignación el supervisor no puede leer', () =>
    assertFails(get(ref(supervisor.database(),'lecturasActuales/worker'))));
  await env.withSecurityRulesDisabled(async ctx => {
    await set(ref(ctx.database(),'permisos/worker/supervisorUid'),'supervisor');
  });
  await check('Realtime Database: el supervisor asignado sí recibe la lectura', async () => {
    const saved = await assertSucceeds(get(ref(supervisor.database(),'lecturasActuales/worker')));
    assert.equal(saved.val().lecturaId,'test-reading');
    await assertFails(get(ref(outsider.database(),'lecturasActuales/worker')));
    await assertFails(get(ref(anonymous.database(),'lecturasActuales/worker')));
  });
  await check('No se pueden alterar permisos, datos de otro trabajador ni el origen simulado', async () => {
    await assertFails(set(ref(worker.database(),'permisos/worker/supervisorUid'),'outsider'));
    await assertFails(set(ref(outsider.database(),'lecturasActuales/worker'),{...reading,actualizadoEn:realtimeTimestamp()}));
    await assertFails(set(ref(worker.database(),'lecturasActuales/worker'),{...reading,origen:'ARDUINO',actualizadoEn:realtimeTimestamp()}));
  });
  console.log(`RESULTADO: ${passed} pruebas superadas en emuladores.`);
} finally { await env.cleanup(); }
