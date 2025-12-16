import 'package:flutter/material.dart';
import 'screens/samsung_health_demo_screen.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '삼성헬스 플러터 데모',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const SamsungHealthDemoScreen(),
    );
  }
}