import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'sendbird_channels.dart';

class HomeScreen extends StatefulWidget {
  @override
  _HomeScreenState createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _calleeController = TextEditingController();

  bool _isCalleeAvailable = false;
  bool _areCalling = false;
  bool _areConnected = false;
  bool _isCallActive = false;
  bool _areReceivingCall = false;

  String? callerId;
  String? callerNickname;
  SendbirdChannels? channels;

  final appId = "4BA72387-8396-42AC-87A0-D88A21079FEA";
  final userId = "aaa";
  final apiToken = "f36452cbd6da3534328212647ae0f53f8eaa8846";

  @override
  void initState() {
    super.initState();
    initSendbird();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Center(child: Text('Sendbird Calls'))),
      body: Container(
        padding: EdgeInsets.fromLTRB(20, 10, 20, 10),
        child: Column(children: [
          Row(children: [
            SizedBox(width: 240, child: Text("Connection status for $userId:")),
            Expanded(child: statusField()),
          ]),
          Container(height: 20),
          // statusField(),
          Row(children: [
            SizedBox(width: 80, child: Text("Calling")),
            Container(width: 10),
            SizedBox(width: 150, child: calleeIdField(_calleeController)),
            Container(width: 10),
            Expanded(
                child: _isCalleeAvailable
                    ? _areConnected && !_isCallActive && !_areCalling
                        ? callButton(_calleeController)
                        : Container()
                    : Container()),
          ]),
          Container(height: 20),
          Row(children: [
            SizedBox(width: 80, child: Text('Receiving')),
            Container(width: 10),
            SizedBox(
              width: 150,
              child: callerNickname != null
                  ? Text('$callerNickname')
                  : callerId != null
                      ? Text("$callerId")
                      : Text("<No incoming calls>"),
            ),
            Expanded(
                child: _areReceivingCall ? receivingCallButton() : Container()),
            Container(height: 20),
          ]),
          Container(height: 10),
          _isCallActive || _areCalling ? hangupButton() : Container(),
        ]),
      ),
    );
  }

  Widget dialRow() {
    return Expanded(
      child: Row(children: [
        Text("Dial"),
        Container(width: 10),
        calleeIdField(_calleeController),
        Container(width: 10),
        _isCallActive ? hangupButton() : callButton(_calleeController)
      ]),
    );
  }

  Widget receiveRow() {
    return Expanded(
      child: Row(children: [
        Text('Receiving calls'),
        Container(width: 10),
        callerNickname != null
            ? Text('$callerNickname')
            : callerId != null
                ? Text("$callerId")
                : Container(),
        _areReceivingCall ? receivingCallButton() : Container(),
        callerId != null && _isCallActive ? hangupButton() : Container(),
      ]),
    );
  }

  Widget statusField() {
    return Container(
        child: _areConnected
            ? Icon(
                Icons.check_circle,
                color: Colors.green,
                size: 40.0,
              )
            : Icon(
                Icons.remove_circle_outline,
                color: Colors.red,
                size: 40.0,
              ));
  }

  Widget calleeIdField(TextEditingController calleeController) {
    return Container(
      child: TextField(
        controller: calleeController,
        onChanged: (text) {
          setState(() {
            _isCalleeAvailable = text.isNotEmpty;
          });
        },
        decoration: InputDecoration(labelText: "Callee User Id"),
      ),
    );
  }

  Widget callButton(TextEditingController controller) {
    return Container(
      child: ElevatedButton(
        onPressed: () async {
          channels?.startCall(controller.text);
          setState(() {
            _areCalling = true;
          });
        },
        child: Icon(
          Icons.call,
          color: Colors.white,
          size: 20.0,
        ),
        style: ElevatedButton.styleFrom(
          shape: CircleBorder(),
          primary: Colors.green, // <-- Button color
          onPrimary: Colors.green, // <-- Splash color
        ),
      ),
    );
  }

  Widget receivingCallButton() {
    return Container(
      child: ElevatedButton(
        onPressed: () {
          channels?.pickupCall();
        },
        child: Icon(
          Icons.call,
          color: Colors.blue,
          size: 20.0,
        ),
        style: ElevatedButton.styleFrom(
          shape: CircleBorder(),
          primary: Colors.white, // <-- Button color
          onPrimary: Colors.white, // <-- Splash color
        ),
      ),
    );
  }

  Widget hangupButton() {
    return Container(
      padding: EdgeInsets.all(20),
      child: ElevatedButton(
        onPressed: () {
          channels?.endCall();
        },
        child: Icon(
          Icons.call_end,
          color: Colors.white,
        ),
        style: ElevatedButton.styleFrom(
          padding: EdgeInsets.all(20),
          shape: CircleBorder(),
          primary: Colors.red, // <-- Button color
          onPrimary: Colors.red, // <-- Splash color
        ),
      ),
    );
  }

  void handleMsg() async {
    print('TOKEN: ${await FirebaseMessaging.instance.getToken()}');
    FirebaseMessaging.onMessage.listen((RemoteMessage event) async {
     await channels?.pickupCall();
    });
  }

  void initSendbird() async {
    channels = SendbirdChannels(directCallReceived: ((userId, nickname) {
      print('directCallReceived');
      setState(() {
        callerId = userId;
        callerNickname = nickname;
        _areReceivingCall = true;
      });
    }), directCallConnected: () {
      setState(() {
        _areCalling = false;
        _areReceivingCall = false;
        _isCallActive = true;
      });
    }, directCallEnded: () {
      setState(() {
        _isCallActive = false;
        _areCalling = false;
        _areReceivingCall = false;
        callerId = null;
        callerNickname = null;
      });
    }, onError: ((message) {
      print(
          "home_screen.dart: initState: SendbirdChannels: onError: message: $message");
    }), onLog: ((message) {
      print(
          "home_screen.dart: initState: SendbirdChannels onLog: message: $message");
    }));
    final pushToken = await FirebaseMessaging.instance.getToken();
    print('pushToken: $pushToken');

    FirebaseMessaging.onMessage.listen((event) {
      print('New MSG: ${event.data}');
    });

    channels
        ?.initSendbird(
          appId: appId,
          userId: userId,
          accessToken: apiToken,
          pushToken: pushToken,
        )
        .then((value) => setState(() {
              _areConnected = value;
            }));
  }
}
