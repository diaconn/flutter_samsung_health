import 'package:flutter/material.dart';

/// 체크박스 섹션을 표시하는 위젯
class CheckboxSectionWidget extends StatelessWidget {
  final String title;
  final bool isExpanded;
  final VoidCallback onToggle;
  final Map<String, bool> checks;
  final Map<String, String> labels;
  final Function(String, bool) onChanged;

  const CheckboxSectionWidget({
    super.key,
    required this.title,
    required this.isExpanded,
    required this.onToggle,
    required this.checks,
    required this.labels,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('  $title', style: Theme.of(context).textTheme.bodyLarge),
            IconButton(
              onPressed: onToggle,
              icon: Icon(isExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down),
            ),
          ],
        ),
        if (isExpanded) ...[
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('$title:', style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold)),
                      Row(
                        children: [
                          TextButton(
                            onPressed: () {
                              for (String key in checks.keys) {
                                onChanged(key, true);
                              }
                            },
                            child: const Text('전체 선택', style: TextStyle(fontSize: 12)),
                          ),
                          TextButton(
                            onPressed: () {
                              for (String key in checks.keys) {
                                onChanged(key, false);
                              }
                            },
                            child: const Text('전체 해제', style: TextStyle(fontSize: 12)),
                          ),
                        ],
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    children: checks.entries.map((entry) {
                      return SizedBox(
                        width: 120,
                        child: CheckboxListTile(
                          dense: true,
                          contentPadding: EdgeInsets.zero,
                          title: Text(labels[entry.key]!, style: const TextStyle(fontSize: 12)),
                          value: entry.value,
                          onChanged: (bool? value) {
                            onChanged(entry.key, value ?? false);
                          },
                        ),
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}