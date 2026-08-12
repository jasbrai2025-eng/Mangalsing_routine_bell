package np.edu.mangalsingh.routine;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeBellPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Exact-alarm Settings बाट फर्किएपछि अनुमति तुरुन्तै लागू हुन्छ।
        NativeBellPlugin.rescheduleFromPrefs(this);
    }
}
