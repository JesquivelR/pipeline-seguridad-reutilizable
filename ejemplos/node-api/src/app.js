'use strict';

const express = require('express');

function crearApp() {
  const app = express();
  app.disable('x-powered-by');

  app.get('/saludo', (req, res) => {
    res.json({ mensaje: 'Hola desde node-api-demo' });
  });

  app.get('/salud', (req, res) => {
    res.json({ estado: 'ok' });
  });

  return app;
}

module.exports = { crearApp };
