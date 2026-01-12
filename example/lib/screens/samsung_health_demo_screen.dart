import 'package:flutter/material.dart';
import '../services/samsung_health_service.dart';
import '../widgets/checkbox_section_widget.dart';
import '../widgets/date_selection_widget.dart';
import '../widgets/result_display_widget.dart';
import '../widgets/status_card_widget.dart';

/// 삼성헬스 데모 메인 화면
class SamsungHealthDemoScreen extends StatefulWidget {
  const SamsungHealthDemoScreen({super.key});

  @override
  State<SamsungHealthDemoScreen> createState() => _SamsungHealthDemoScreenState();
}

class _SamsungHealthDemoScreenState extends State<SamsungHealthDemoScreen> {
  final _service = SamsungHealthService();
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _service.checkInstallation();
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  /// 버튼 빌더
  Widget _buildButton(String text, VoidCallback? onPressed) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: ElevatedButton(
        onPressed: onPressed,
        child: Text(text),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _service,
      builder: (context, child) => Scaffold(
        appBar: AppBar(
          title: const Text('삼성 헬스 데모'),
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        ),
        body: SingleChildScrollView(
          controller: _scrollController,
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 상태 정보 카드
              StatusCardWidget(
                installStatus: _service.installStatus,
                connectionStatus: _service.connectionStatus,
                grantedPermissions: _service.grantedPermissions,
              ),
              
              const SizedBox(height: 16),
              
              // 마지막 결과
              ResultDisplayWidget(
                lastResult: _service.lastResult,
                isResultExpanded: _service.isResultExpanded,
                onToggleExpanded: _service.toggleResultExpanded,
              ),
              
              const SizedBox(height: 16),
              
              // Samsung Health 앱 관리
              Text('삼성헬스 앱', style: Theme.of(context).textTheme.headlineSmall),
              _buildButton('설치 상태 확인', () => _service.checkInstallation()),
              _buildButton('삼성헬스 열기', () => _service.openSamsungHealthApp()),
              
              const SizedBox(height: 16),
              
              // 연결 관리
              Text('연결', style: Theme.of(context).textTheme.headlineSmall),
              _buildButton('연결하기', () => _service.connect()),
              _buildButton('연결 해제', _service.isConnected ? () => _service.disconnect() : null),
              
              const SizedBox(height: 16),
              
              // 권한 관리
              Text('권한 관리', style: Theme.of(context).textTheme.headlineSmall),
              CheckboxSectionWidget(
                title: '권한 선택',
                isExpanded: _service.showPermissionSelection,
                onToggle: _service.togglePermissionSelection,
                checks: _service.permissionChecks,
                labels: _service.permissionLabels,
                onChanged: _service.updatePermissionCheck,
              ),
              _buildButton('선택한 권한 요청', _service.isConnected ? () => _service.requestPermissions() : null),
              _buildButton('승인된 권한 조회', _service.isConnected ? () => _service.getGrantedPermissions() : null),
              
              const SizedBox(height: 16),
              
              // 옵저버 관리
              Text('옵저버 관리', style: Theme.of(context).textTheme.headlineSmall),
              CheckboxSectionWidget(
                title: '옵저버 권한 선택',
                isExpanded: _service.showObserverSelection,
                onToggle: _service.toggleObserverSelection,
                checks: _service.observerChecks,
                labels: _service.observerLabels,
                onChanged: _service.updateObserverCheck,
              ),
              _buildButton('선택한 데이터 옵저버 시작', _service.isConnected ? () => _service.startObserver() : null),
              _buildButton('선택한 데이터 옵저버 중지', _service.isConnected ? () => _service.stopObserver() : null),
              _buildButton('옵저버 상태 확인', _service.isConnected ? () => _service.getObserverStatus() : null),
              
              const SizedBox(height: 16),
              
              // 데이터 조회
              Text('데이터 조회', style: Theme.of(context).textTheme.headlineSmall),
              
              // 날짜 선택
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('  조회 기간 설정', style: Theme.of(context).textTheme.bodyLarge),
                  IconButton(
                    onPressed: _service.toggleDateSelection,
                    icon: Icon(_service.showDateSelection ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down),
                  ),
                ],
              ),
              if (_service.showDateSelection) ...[
                DateSelectionWidget(
                  startDate: _service.startDate,
                  endDate: _service.endDate,
                  onStartDateChanged: _service.updateStartDate,
                  onEndDateChanged: _service.updateEndDate,
                ),
              ],
              
              // 데이터 조회 버튼들
              _buildButton('전체 데이터 조회', _service.isConnected ? () => _service.getTotalData() : null),
              _buildButton('운동 데이터 조회', _service.isConnected ? () => _service.getExerciseData(_service.startDate, _service.endDate) : null),
              _buildButton('심박수 데이터 조회', _service.isConnected ? () => _service.getHeartRateData(_service.startDate, _service.endDate) : null),
              _buildButton('걸음수 데이터 조회', _service.isConnected ? () => _service.getStepsData(_service.startDate, _service.endDate) : null),
              _buildButton('5분 간격 걸음수 조회', _service.isConnected ? () => _service.getFiveMinuteStepsData(_service.startDate, _service.endDate) : null),
              _buildButton('5분 간격 걸음수 샘플 데이터', () => _service.generateFiveMinuteStepsSampleData()),
              _buildButton('수면 데이터 조회', _service.isConnected ? () => _service.getSleepData(_service.startDate, _service.endDate) : null),
              _buildButton('영양소 데이터 조회', _service.isConnected ? () => _service.getNutritionData(_service.startDate, _service.endDate) : null),
              _buildButton('신체구성 데이터 조회', _service.isConnected ? () => _service.getBodyCompositionData(_service.startDate, _service.endDate) : null),
              _buildButton('산소포화도 데이터 조회', _service.isConnected ? () => _service.getOxygenSaturationData(_service.startDate, _service.endDate) : null),
              _buildButton('체온 데이터 조회', _service.isConnected ? () => _service.getBodyTemperatureData(_service.startDate, _service.endDate) : null),
              _buildButton('혈당 데이터 조회', _service.isConnected ? () => _service.getBloodGlucoseData(_service.startDate, _service.endDate) : null),
              
              // 하단 여백
              const SizedBox(height: 100),
            ],
          ),
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: () {
            _scrollController.animateTo(
              0,
              duration: const Duration(milliseconds: 500),
              curve: Curves.easeInOut,
            );
          },
          shape: const CircleBorder(),
          tooltip: '맨 위로',
          child: const Icon(Icons.keyboard_arrow_up),
        ),
      ),
    );
  }
}