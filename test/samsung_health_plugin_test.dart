import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:samsung_health_plugin/samsung_health_plugin.dart';
import 'package:samsung_health_plugin/samsung_health_plugin_method_channel.dart';
import 'package:samsung_health_plugin/samsung_health_plugin_platform_interface.dart';

class MockSamsungHealthPluginPlatform with MockPlatformInterfaceMixin implements SamsungHealthPluginPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  Future<List<Map<String, dynamic>>> getHeartRate5minSeries(int startMillis, int endMillis) {
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
}

void main() {
  final SamsungHealthPluginPlatform initialPlatform = SamsungHealthPluginPlatform.instance;

  test('$MethodChannelSamsungHealthPlugin is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelSamsungHealthPlugin>());
  });

  test('getPlatformVersion', () async {
    SamsungHealthPlugin samsungHealthPlugin = SamsungHealthPlugin();
    MockSamsungHealthPluginPlatform fakePlatform = MockSamsungHealthPluginPlatform();
    SamsungHealthPluginPlatform.instance = fakePlatform;

    expect(await samsungHealthPlugin.getPlatformVersion(), '42');
  });
}
