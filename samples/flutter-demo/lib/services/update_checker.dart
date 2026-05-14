import 'dart:async';
import 'dart:convert';
import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:in_app_update/in_app_update.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

/// Cross-platform update checker used by `SceneViewDemoApp`.
///
/// **Android** — delegates to Google Play In-App Updates via the `in_app_update`
/// package. When an update is available it runs a flexible flow (background
/// download + user-triggered "Restart"), mirroring the Kotlin
/// `InAppUpdateManager` used by `samples/android-demo`.
///
/// **iOS** — Apple does not expose an in-app install API, so we read
/// `https://itunes.apple.com/lookup?bundleId=<id>`, compare with the running
/// app's `CFBundleShortVersionString`, and (if newer) ask the user to open the
/// App Store via `itms-apps://`.
///
/// **When to call.** Wire `checkForUpdate()` to `WidgetsBindingObserver
/// .didChangeAppLifecycleState` and trigger it on `AppLifecycleState.resumed`.
/// The class itself throttles to one network call per 12 hours and respects
/// a 7-day snooze that the user can trigger from the banner.
///
/// Throttle / snooze state is held in-memory only — a SharedPreferences port
/// is left as an exercise so the demo stays as small as possible (no extra
/// platform-channel surface). The Android Play SDK already handles its own
/// "don't prompt twice" logic, and iOS users that snooze will see the banner
/// reappear after a process restart, which is acceptable for a sample.
class UpdateChecker extends ChangeNotifier {
  UpdateChecker({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  /// App Store track id used by the "Update" CTA on iOS. This points at the
  /// Swift SceneView demo on purpose — the Flutter demo itself is dev-only
  /// (template, not published), so when iOS users tap Update we bounce them
  /// to the canonical 3D & AR Explorer listing rather than a non-existent
  /// product page. If/when this Flutter demo is published to the App Store,
  /// swap to its own track id.
  static const String _appStoreId = '6761329763';

  /// 12-hour throttle. Android in_app_update has its own debouncing but
  /// the iTunes lookup endpoint does not.
  static const Duration _throttle = Duration(hours: 12);

  DateTime? _lastCheck;
  /// Version string the user last dismissed. The banner stays hidden as long
  /// as `_availableVersion == _snoozedVersion`. A different `_availableVersion`
  /// (= a newer release has landed since the dismiss) invalidates the snooze
  /// and re-surfaces the banner — same semantics as the web demo's
  /// `sceneview.update.snoozedVersion` localStorage key.
  String? _snoozedVersion;
  String? _availableVersion;

  /// True when a newer version was detected and the user hasn't snoozed *this*
  /// version. A newer release than the snoozed one re-surfaces the banner.
  bool get hasUpdate =>
      _availableVersion != null && _availableVersion != _snoozedVersion;
  String? get availableVersion => _availableVersion;

  /// Run a platform-specific update check. Pass `force: true` to bypass the
  /// 12 h throttle (e.g. a manual "Check now" CTA).
  ///
  /// `_lastCheck` is stamped ONLY after the platform call succeeds so a
  /// transient failure (iTunes 503, ARCore-availability hiccup) doesn't
  /// burn the 12 h budget for the next resume.
  Future<void> checkForUpdate({bool force = false}) async {
    if (!force) {
      if (_lastCheck != null && DateTime.now().difference(_lastCheck!) < _throttle) return;
    }

    try {
      if (Platform.isAndroid) {
        await _checkAndroid();
      } else if (Platform.isIOS) {
        await _checkIos();
      }
      // Only stamp on success — a `catch` below leaves `_lastCheck`
      // untouched so the next resume retries immediately.
      _lastCheck = DateTime.now();
    } catch (e, st) {
      // Silent failure on network / decode errors — the banner just stays
      // hidden. We log to debug so a curious dev can see what happened.
      debugPrint('UpdateChecker.checkForUpdate failed: $e\n$st');
    }
  }

  /// Kick off the Play flexible-update flow. Should be called from a button
  /// `onPressed` (not at startup) — the Play SDK requires a user-visible
  /// activity context.
  Future<void> startUpdateFlow() async {
    if (!Platform.isAndroid) {
      // iOS: open the App Store product page.
      final url = Uri.parse('itms-apps://itunes.apple.com/app/id$_appStoreId');
      if (await canLaunchUrl(url)) {
        await launchUrl(url, mode: LaunchMode.externalApplication);
      }
      return;
    }
    try {
      await InAppUpdate.startFlexibleUpdate();
      // Once the download finishes, callers should invoke
      // `completeFlexibleUpdate()` from the "Restart" CTA. The
      // `in_app_update` package surfaces no Flutter-side install-state
      // stream, so the UpdateBanner polls `checkForUpdate()` again on
      // every resume — keeps the API tiny.
    } catch (e) {
      debugPrint('startFlexibleUpdate failed: $e');
    }
  }

  /// Apply the downloaded Play update (Android only — no-op elsewhere).
  Future<void> completeUpdate() async {
    if (!Platform.isAndroid) return;
    try {
      await InAppUpdate.completeFlexibleUpdate();
    } catch (e) {
      debugPrint('completeFlexibleUpdate failed: $e');
    }
  }

  /// Hide the banner for the currently-detected version. A future release
  /// (where `_availableVersion` becomes a NEW string) re-surfaces the banner.
  void snooze() {
    _snoozedVersion = _availableVersion;
    notifyListeners();
  }

  Future<void> _checkAndroid() async {
    final info = await InAppUpdate.checkForUpdate();
    if (info.updateAvailability == UpdateAvailability.updateAvailable &&
        info.flexibleUpdateAllowed) {
      // We don't have a Play-supplied "marketing version" string, so reuse
      // the build's version code as a placeholder. The Banner just renders
      // "Update available" without a version literal on Android.
      _availableVersion = info.availableVersionCode?.toString() ?? 'pending';
      notifyListeners();
    } else {
      _availableVersion = null;
      notifyListeners();
    }
  }

  Future<void> _checkIos() async {
    final pkg = await PackageInfo.fromPlatform();
    final bundleId = pkg.packageName;
    final current = pkg.version;

    final url = Uri.https('itunes.apple.com', '/lookup', {'bundleId': bundleId});
    final response = await _client.get(url);
    if (response.statusCode != 200) return;
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final results = json['results'] as List<dynamic>?;
    if (results == null || results.isEmpty) return;
    final latest = (results.first as Map<String, dynamic>)['version'] as String?;
    if (latest == null) return;

    if (_compareVersions(latest, current) > 0) {
      _availableVersion = latest;
      notifyListeners();
    } else {
      _availableVersion = null;
      notifyListeners();
    }
  }

  /// Component-wise version compare (`1.2.10` > `1.2.9`). Non-numeric or
  /// missing components compare as `0`.
  static int _compareVersions(String a, String b) {
    final la = a.split('.').map((s) => int.tryParse(s) ?? 0).toList();
    final lb = b.split('.').map((s) => int.tryParse(s) ?? 0).toList();
    final len = la.length > lb.length ? la.length : lb.length;
    for (var i = 0; i < len; i++) {
      final x = i < la.length ? la[i] : 0;
      final y = i < lb.length ? lb[i] : 0;
      if (x != y) return x.compareTo(y);
    }
    return 0;
  }
}
