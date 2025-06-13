import 'package:flutter_samsung_health/flutter_samsung_health_method_channel.dart';
import 'package:flutter_samsung_health/flutter_samsung_health_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockSamsungHealthPluginPlatform with MockPlatformInterfaceMixin implements FlutterSamsungHealthPlatform {
  @override
  Future<List<Map<String, dynamic>>> getHeartRate5minSeries(int start, int end) {
    // TODO: implement getHeartRate5minSeries
    throw UnimplementedError();
  }

  @override
  Future<Map<String, dynamic>> connect() {
    // TODO: implement connect
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> getExerciseSessions(int start, int end) {
    // TODO: implement getExerciseSessions
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> getSleepData(int start, int end) {
    // TODO: implement getSleepData
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> getStepCountSeries(int start, int end) {
    // TODO: implement getStepCountSeries
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> getNutritionData(int start, int end) {
    // TODO: implement getNutritionData
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> getHeartRateData(int start, int end) {
    // TODO: implement getHeartRateData
    throw UnimplementedError();
  }

  @override
  Future<List<Map<String, dynamic>>> getSleepStageData(int start, int end) {
    // TODO: implement getSleepStageData
    throw UnimplementedError();
  }
}

void main() {
  final FlutterSamsungHealthPlatform initialPlatform = FlutterSamsungHealthPlatform.instance;

  test('$MethodChannelFlutterSamsungHealth is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterSamsungHealth>());
  });
}
