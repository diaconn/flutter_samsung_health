import 'dart:convert';
import 'package:flutter/material.dart';

/// API 결과를 표시하는 위젯
class ResultDisplayWidget extends StatelessWidget {
  final Map<String, dynamic>? lastResult;
  final bool isResultExpanded;
  final VoidCallback onToggleExpanded;

  const ResultDisplayWidget({
    super.key,
    required this.lastResult,
    required this.isResultExpanded,
    required this.onToggleExpanded,
  });

  /// JSON 결과 포맷터
  String _formatResult(Map<String, dynamic> result) {
    const encoder = JsonEncoder.withIndent('  ');
    return encoder.convert(result);
  }

  /// 결과 표시용 텍스트 생성
  String _getDisplayResult() {
    if (lastResult == null) return '';
    
    String fullResult = _formatResult(lastResult!);
    List<String> lines = fullResult.split('\n');
    
    if (lines.length <= 15 || isResultExpanded) {
      return fullResult;
    } else {
      return lines.take(15).join('\n') + '\n\n... ${lines.length - 15}줄 더 있음';
    }
  }

  @override
  Widget build(BuildContext context) {
    if (lastResult == null) return const SizedBox.shrink();

    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Text('결과', style: Theme.of(context).textTheme.headlineSmall),
                if (lastResult!['realtime'] == true)
                  Container(
                    margin: const EdgeInsets.only(left: 8),
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.green,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Text(
                      '실시간',
                      style: TextStyle(color: Colors.white, fontSize: 10),
                    ),
                  ),
              ],
            ),
            if (_formatResult(lastResult!).split('\n').length > 15)
              TextButton(
                onPressed: onToggleExpanded,
                child: Text(isResultExpanded ? '접기' : '펼치기', style: const TextStyle(fontSize: 12)),
              ),
          ],
        ),
        SizedBox(
          width: double.infinity,
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Container(
                  constraints: BoxConstraints(
                    maxHeight: isResultExpanded ? double.infinity : 225,
                  ),
                  child: SingleChildScrollView(
                    child: Text(
                      _getDisplayResult(),
                      style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}