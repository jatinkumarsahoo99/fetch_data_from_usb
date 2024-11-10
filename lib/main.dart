import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  static const platform = MethodChannel('usb_serial_channel');
  String status = 'Disconnected';
  List<String> receivedData = [];

  @override
  void initState() {
    super.initState();
    _startListeningForData();
  }

  Future<void> _requestUsbPermission() async {
    try {
      final result = await platform.invokeMethod('requestUsbPermission');
      setState(() {
        status = result;
      });
    } on PlatformException catch (e) {
      setState(() {
        status = "Error: ${e.message}";
      });
    }
  }

  Future<void> _startSerialStream() async {
    try {
      final result = await platform.invokeMethod('startSerialStream');
      setState(() {
        status = result;
      });
    } on PlatformException catch (e) {
      setState(() {
        status = "Error: ${e.message}";
      });
    }
  }

  Future<void> _stopSerialStream() async {
    try {
      final result = await platform.invokeMethod('stopSerialStream');
      setState(() {
        status = result;
      });
    } on PlatformException catch (e) {
      setState(() {
        status = "Error: ${e.message}";
      });
    }
  }

  void _startListeningForData() {
    platform.setMethodCallHandler((call) async {
      if (call.method == 'onDataReceived') {
        setState(() {
          receivedData.add(call.arguments as String);
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: Text('USB Serial Demo'),
        ),
        body: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Text('Status: $status', style: TextStyle(fontSize: 18)),
            ),
            ElevatedButton(
              onPressed: _requestUsbPermission,
              child: Text('Request USB Permission'),
            ),
            ElevatedButton(
              onPressed: _startSerialStream,
              child: Text('Start Serial Stream'),
            ),
            ElevatedButton(
              onPressed: _stopSerialStream,
              child: Text('Stop Serial Stream'),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: receivedData.length,
                itemBuilder: (context, index) {
                  return ListTile(
                    title: Text(receivedData[index]),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
