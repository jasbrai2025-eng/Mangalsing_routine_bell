# MS Routine — Android APK (Exact Background Bell)

यो संस्करण PWA मात्र होइन; Android native `AlarmManager.setExactAndAllowWhileIdle()` प्रयोग गर्छ।
त्यसैले फोन lock/sleep/Doze मा हुँदा र app Recent Apps बाट swipe गरेर बन्द गर्दा पनि
OS-level alarm ले निर्धारित समयमा bell receiver चलाउँछ।

## मुख्य सुविधा
- ठ्याक्कै समयमा दैनिक school bell
- फोन lock/sleep/Doze मा पनि
- Recent Apps बाट app बन्द गरेपछि पनि
- फोन restart पछि BootReceiver बाट schedule पुनःस्थापना
- Android 12+ Exact Alarm permission
- Android 13+ Notification permission
- Battery optimisation exemption को अनुरोध
- `bell.wav` custom school bell
- Routine change हुँदा पुराना alarms हटाएर नयाँ schedule

## Build
GitHub repository मा यो folder push गर्नुहोस्। Actions → Build MS Routine Android APK।
Build सकिएपछि `app-debug.apk` artifact वा Release बाट लिनुहोस्।

## पहिलो पटक फोनमा
1. APK install गर्नुहोस्।
2. MS Routine खोल्नुहोस्।
3. "पृष्ठभूमि सूचना" सक्रिय गर्नुहोस्।
4. Notification permission Allow गर्नुहोस्।
5. Exact alarm permission Allow गर्नुहोस्।
6. Battery optimisation मा Unrestricted/Don't optimize अनुमति दिनुहोस्।
7. एपभित्र Test/परीक्षण बटनबाट bell जाँच गर्नुहोस्।

### महत्त्वपूर्ण
फोन पूर्ण Silent/DND मा छ भने Android को system policy अनुसार आवाज रोक्न सक्छ।
Xiaomi/Oppo/Vivo/Samsung जस्ता फोनमा "Battery → Unrestricted/No restrictions"
पनि राख्नु उत्तम हुन्छ।

यो APK debug/sideload build हो। Play Store का लागि signed release build छुट्टै चाहिन्छ।


## v3.0 महत्वपूर्ण सुधार
- Exact Alarm permission दिएर Settings बाट फर्किएपछि alarms स्वतः schedule हुन्छन्।
- `AlarmManager.setAlarmClock()` प्रयोग गरिएको छ, जसले sleep/Doze अवस्थामा पनि wake-up alarm राख्छ।
- bell sound लाई `USAGE_ALARM` का साथ native MediaPlayer + WakeLock बाट बजाइन्छ।
- bellCount अनुसार 2-second interval मा छुट्टाछुट्टै exact alarms राखिन्छन्।
- Test button ले native bell test गर्छ।
