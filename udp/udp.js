var dgram = require('dgram');
var server = dgram.createSocket('udp4');
var base64 = require('base-64');
var noble = require('noble');

/**
  * Following instances and methods are for BLE communication.
  * To use BLE, you should run this node.js server with ROOT permission.
  */

var csma_cn_service_uuid = 'c95457bee2794e279bb94a34c4ecfeb4';
var peri = null;
var characteristic = null;
var collisionNotified = false;

noble.on('stateChange', function(state){
	if(state == 'poweredOn') console.log('BLE powered on. now is able to scan');
	else console.log('state changed to %s.', state);
});

noble.on('warning', function(message){
	console.log('warning message: %s', message);
});

noble.on('discover', function(peripheral){
	if(peripheral.advertisement.serviceData.left <= 0 ||
		peripheral.advertisement.serviceData[0].data.toString('ascii') != 'csmacn') return;

	peripheral.connect(function(error){
		if(error) throw error;
		console.log('[BLE] connected to peripheral ' + peripheral.address);

		peripheral.discoverServices([csma_cn_service_uuid], function(error, services){
			if(error) throw error; if(services.length < 1) return;
			var csmacnService = services[0];
			console.log('[BLE] discovered CSMA/CN BLE services');

			csmacnService.discoverCharacteristics(null, function(error, characteristics){
				if(error) throw error;

				peri = peripheral;
				characteristic = characteristics[0];
				noble.stopScanning();
				
				characteristics[0].write(new Buffer([0]), false);
			});
		});
	});
});


/**
  * Following instances and methods are for simulating hidden terminal problem.
  * When variable interleaved is set true, some other node is also sending so the AP cannot respond.
  */

// console.log(process.argv);

if(process.argv.length != 4) throw new Error('Usage: node udp.js [Hiddern Terminal Option] [CSMA/CN Option]. Please run again with appropriate arguments');

var interleaved = false;
var interleavingTime = 3;
var minInterval = 10;
var maxInterval = 50;
var enableInterleaved = (process.argv[2] == 'true');
var bleCollisionNotification = (process.argv[3] == 'true');

var timer;
function setInterleaved() {
	interleaved = true;
	setTimeout(function(){
		interleaved = false;
	}, interleavingTime);
}

function randomSetInterleaved() {
	var rand = Math.round(Math.random() * (maxInterval - minInterval) + minInterval);
	timer = setTimeout(function() {
		setInterleaved();
		randomSetInterleaved();
	}, rand);

}

function resetCollisionNotified() {
	setTimeout(function(){
		collisionNotified = false;
		resetCollisionNotified();
	}, 20);
}

if(enableInterleaved) randomSetInterleaved();
if(bleCollisionNotification) resetCollisionNotified();

/**
  * Following methods are for UDP datagram server.
  */

var shouldSendACK = false;
var nextExpectedPacketSeq = 0;
var connected = false;

function send(msg, address, port){
	server.send(msg, 0, msg.length, port, address);
}

server.on('message', function (msg, rinfo){
	msg = JSON.parse(msg);
	if(connected){
		if(msg.type == 'data'){
			// if interleaved true, server can do nothing
			if(!collisionNotified && enableInterleaved && interleaved){
				// if bleCollisionNotification setting is on,
				// send collision notification every 20ms by setting collisionNotified variable as true/false.
				if(bleCollisionNotification) {
					characteristic.write(new Buffer([5]), true);
					// in this case, interleaving device also received collision notification,
					// this interleaving is aborted.
					interleaved = false;
					collisionNotified = true;
					clearTimeout(timer);
					randomSetInterleaved();
				}
			}
			else if(msg.seq == nextExpectedPacketSeq){
				// TODO: When interference on, it should not accept data.
				// if interleaved == true, it should be handled as unordered situation.
				nextExpectedPacketSeq++;
				if(shouldSendACK) {
					var data = {
						'seq': msg.seq,
						'ack': nextExpectedPacketSeq
					};
					data.ack = nextExpectedPacketSeq;
					send(JSON.stringify(data), rinfo.address, rinfo.port);
					shouldSendACK = false;
//					console.log('send ACK %d', nextExpectedPacketSeq);
				}
				else shouldSendACK = true;
//				console.log('data index %d successfully received length : %d', msg.seq, msg.data.length);
			}
			else{
				var data = {
					'seq': msg.seq,
					'ack': nextExpectedPacketSeq
				};
//				console.log('data received unorder. expected: %d, received: %d', nextExpectedPacketSeq, msg.seq);
				send(JSON.stringify(data), rinfo.address, rinfo.port);
			}
		}
		else if(msg.type == 'disconnect'){
			console.log('Disconnection request received. disconnecting..');
			send('ack', rinfo.address, rinfo.port);
			connected = false;
			console.log('connection disconnected');
			if(enableInterleaved && bleCollisionNotification){
				peri.disconnect(function(error){
					if(error) throw error;
					peri = null;
				});
			}
		}
	}
	else if(msg.type == 'connect'){
		if(!connected){
			console.log('connection ack received from client %s:%d', rinfo.address, rinfo.port);
			var data = {
				'ack': true,
				'hiddenterminal': enableInterleaved,
				'csmacn': bleCollisionNotification
			};
			connected = true;
			nextExpectedPacketSeq = 0;	
			send(JSON.stringify(data), rinfo.address, rinfo.port);
			if(enableInterleaved && bleCollisionNotification) noble.startScanning([csma_cn_service_uuid], false);
		}
		else{
			console.log('connection request during communication. reject');
			var data = {
				'ack': false
			};
			send(JSON.stringify(data), rinfo.address, rinfo.port);
		}
	}
});

server.bind(5000, function(){
	console.log('UDP server is on port 5000');		
	if(enableInterleaved) console.log('Hidden Terminal Problem [on] ' +
			'BLE collision notification is [' + (bleCollisionNotification == true? 'on' : 'off') + ']\n' +
			'Hidden node is accessing ' + interleavingTime + ' ms every ' +
			'[' + minInterval + ' - ' + maxInterval + '] ms\n');
	else console.log('Hidden Terminal Problem [off]');
});
