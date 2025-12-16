import 'package:flutter_samsung_health/flutter_samsung_health_method_channel.dart';
import 'package:flutter_samsung_health/flutter_samsung_health_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockSamsungHealthPluginPlatform with MockPlatformInterfaceMixin implements FlutterSamsungHealthPlatform {
  @override
  Future<Map<String, dynamic>> isSamsungHealthInstalled() async {
    return {'isInstalled': true};
  }

  @override
  Future<Map<String, dynamic>> openSamsungHealth() async {
    return {'action': 'success'};
  }

  @override
  Future<Map<String, dynamic>> connect() async {
    return {'isConnect': true};
  }

  @override
  Future<Map<String, dynamic>> disconnect() async {
    return {'isConnect': false};
  }

  @override
  Future<Map<String, dynamic>> requestPermissions(List<String>? types) async {
    return {'isGranted': true};
  }

  @override
  Future<Map<String, dynamic>> getGrantedPermissions() async {
    return {'granted': [], 'isGranted': false};
  }

  @override
  Future<Map<String, List<Map<String, dynamic>>>> getTotalData(int start, int end) async {
    return {};
  }

  @override
  Future<Map<String, dynamic>> openSamsungHealthPermissions() async {
    return {'action': 'success'};
  }

  @override
  Future<Map<String, dynamic>> startObserver(List<String>? dataTypes) async {
    return {'started': [], 'already_running': []};
  }

  @override
  Future<Map<String, dynamic>> stopObserver(List<String>? dataTypes) async {
    return {'stopped': [], 'not_running': []};
  }

  @override
  Future<dynamic> getObserverStatus(List<String>? dataTypes) async {
    return {'running': [], 'stopped': []};
  }

  @override
  Future<Map<String, dynamic>> getExerciseData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getHeartRateData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getSleepData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getStepsData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getFiveMinuteStepsData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getNutritionData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getBodyCompositionData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getOxygenSaturationData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getBodyTemperatureData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }

  @override
  Future<Map<String, dynamic>> getBloodGlucoseData(int start, int end) async {
    return {'success': true, 'result': [], 'message': 'Test data'};
  }
}

void main() {
  final FlutterSamsungHealthPlatform initialPlatform = FlutterSamsungHealthPlatform.instance;

  test('$MethodChannelFlutterSamsungHealth is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterSamsungHealth>());
  });
}
