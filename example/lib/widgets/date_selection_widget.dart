import 'package:flutter/material.dart';

/// 날짜 범위 선택 위젯
class DateSelectionWidget extends StatelessWidget {
  final DateTime startDate;
  final DateTime endDate;
  final Function(DateTime) onStartDateChanged;
  final Function(DateTime) onEndDateChanged;

  const DateSelectionWidget({
    super.key,
    required this.startDate,
    required this.endDate,
    required this.onStartDateChanged,
    required this.onEndDateChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('조회 기간:', style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('시작 날짜', style: Theme.of(context).textTheme.bodySmall),
                      TextButton(
                        onPressed: () async {
                          final picked = await showDatePicker(
                            context: context,
                            initialDate: startDate,
                            firstDate: DateTime(2020),
                            lastDate: DateTime.now(),
                          );
                          if (picked != null && picked != startDate) {
                            onStartDateChanged(picked);
                          }
                        },
                        child: Text('${startDate.year}-${startDate.month.toString().padLeft(2, '0')}-${startDate.day.toString().padLeft(2, '0')}'),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('종료 날짜', style: Theme.of(context).textTheme.bodySmall),
                      TextButton(
                        onPressed: () async {
                          final picked = await showDatePicker(
                            context: context,
                            initialDate: endDate,
                            firstDate: DateTime(2020),
                            lastDate: DateTime.now(),
                          );
                          if (picked != null && picked != endDate) {
                            onEndDateChanged(picked);
                          }
                        },
                        child: Text('${endDate.year}-${endDate.month.toString().padLeft(2, '0')}-${endDate.day.toString().padLeft(2, '0')}'),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                TextButton(
                  onPressed: () {
                    final now = DateTime.now();
                    onEndDateChanged(now);
                    onStartDateChanged(now.subtract(const Duration(days: 1)));
                  },
                  child: const Text('1일', style: TextStyle(fontSize: 12)),
                ),
                TextButton(
                  onPressed: () {
                    final now = DateTime.now();
                    onEndDateChanged(now);
                    onStartDateChanged(now.subtract(const Duration(days: 7)));
                  },
                  child: const Text('7일', style: TextStyle(fontSize: 12)),
                ),
                TextButton(
                  onPressed: () {
                    final now = DateTime.now();
                    onEndDateChanged(now);
                    onStartDateChanged(now.subtract(const Duration(days: 30)));
                  },
                  child: const Text('30일', style: TextStyle(fontSize: 12)),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}