import 'package:flutter/material.dart';
import 'package:flutter_samsung_health/flutter_samsung_health.dart';

/// 상태 정보를 표시하는 카드 위젯
class StatusCardWidget extends StatelessWidget {
  final String installStatus;
  final String connectionStatus;
  final List<SamsungHealthDataType> grantedPermissions;

  const StatusCardWidget({
    super.key,
    required this.installStatus,
    required this.connectionStatus,
    required this.grantedPermissions,
  });

  @override
  Widget build(BuildContext context) {
    final permissionNames = grantedPermissions.map((e) => e.value).join(', ');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('상태', style: Theme.of(context).textTheme.headlineSmall),
        SizedBox(
          width: double.infinity,
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('설치 상태: $installStatus'),
                  Text('연결 상태: $connectionStatus'),
                  Text('허용된 권한: $permissionNames'),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}