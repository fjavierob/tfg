# Trabajo Fin de Grado

Grado en Ingeniería de las Tecnologías de Telecomunicación.

Universidad de Sevilla.

### Tema

Monitorización del pulso de usuario mediante la lectura de sensores Bluetooth LE usando Eclipse Kura y AWS IoT.

### Resumen

Para este trabajo se ha realizado un sistema que permite monitorizar la información generada por múltiples sensores de pulso cardíaco que usan Bluetooth Low Energy.

El sistema consta de tres partes diferenciadas:

* **Bundle HRPClient**: Una aplicación en Java para Eclipse Kura que se conecta a los sensores Bluetooth LE de pulso cercanos, obtiene información de ellos y la publica en la nube. Está pensado para que corra en una Raspberry Pi con Eclipse Kura.

* **AWS**: Se usa el servicio **AWS IoT** para el intercambio de información entre el bundle HRPClient y el usuario final (mediante una aplicación Android). Además también se usan los servicios **DynamoDB** (persistencia de la información) y **Amazon Cognito** (obtención de credenciales).

* **Aplicación Android**: Se ha desarrollado una aplicación para controlar el funcionamiento del bundle HRPClient y para recibir en tiempo real toda la información recolectada por este (para ello, la aplicación se suscribe al topic en el que el bundle HRPClient en cuestión publica la información). La información la muestra en la pantalla conforme llega y también se elabora una gráfica para cada sensor de pulso con sus diferentes medidas de pulso en función del tiempo.

Este sistema está pensado para que sea usado por gimnasios, en los que en cada aula existe una Raspberry Pi con Eclipse Kura y el bundle HRPClient, y un monitor que va a dar una clase puede, mediante un dispositivo Android, monitorizar el pulso de sus alumnos.

El sistema puede ser usado por diferentes dispositivos en diferentes gimnasios al mismo tiempo sin ningún problema. Cada gimnasio se identifica por un código y tiene su propia identidad (certificado y clave privada) y dentro de un gimnasio cada dispositivo debe ser identificado por un número distinto.

## Autor

**Francisco Javier  Ortiz Bonilla** - [Pogorelich](https://github.com/pogorelich)
