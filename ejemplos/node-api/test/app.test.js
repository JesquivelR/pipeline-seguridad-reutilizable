'use strict';

const test = require('node:test');
const assert = require('node:assert');
const { crearApp } = require('../src/app');

async function consultar(ruta) {
  const servidor = crearApp().listen(0);
  try {
    const { port } = servidor.address();
    const respuesta = await fetch(`http://127.0.0.1:${port}${ruta}`);
    return { estado: respuesta.status, cuerpo: await respuesta.json() };
  } finally {
    servidor.close();
  }
}

test('GET /saludo devuelve el mensaje', async () => {
  const { estado, cuerpo } = await consultar('/saludo');
  assert.strictEqual(estado, 200);
  assert.strictEqual(cuerpo.mensaje, 'Hola desde node-api-demo');
});

test('GET /salud devuelve ok', async () => {
  const { estado, cuerpo } = await consultar('/salud');
  assert.strictEqual(estado, 200);
  assert.strictEqual(cuerpo.estado, 'ok');
});
