'use strict';

const { crearApp } = require('./app');

const puerto = Number(process.env.PORT) || 3000;
crearApp().listen(puerto, () => {
  console.log(`Escuchando en el puerto ${puerto}`);
});
