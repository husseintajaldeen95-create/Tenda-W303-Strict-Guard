package com.tenda.w303guard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * First-stage Android companion for the legacy Tenda W303R web interface.
 * It deliberately uses the router's own UI in a local-only WebView until the
 * exact V5.07.46_en form fields and write semantics have been verified.
 */
public class MainActivity extends Activity {
    private static final String DEFAULT_ROUTER = "192.168.0.1";
    private static final int ORANGE = Color.rgb(232, 117, 0);
    private static final int DARK = Color.rgb(31, 41, 55);
    private static final int MUTED = Color.rgb(90, 99, 110);
    private static final int BORDER = Color.rgb(224, 228, 233);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText routerIp;
    private WebView webView;
    private TextView statusView;
    private TextView pageView;
    private TextView devicesView;
    private TextView diagnosticView;
    private String activeHost = DEFAULT_ROUTER;
    private String lastDiagnostic = "";
    private String lastDevices = "";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        configureWebView();
    }

    private void buildUi() {
        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(Color.rgb(247, 248, 250));
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(20));
        outer.addView(root);

        TextView title = text("Tenda W303 Guard", 23, true, DARK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());
        TextView subtitle = text("إدارة محلية لراوتر Tenda W303R", 14, false, MUTED);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap());
        gap(root, 12);

        LinearLayout connection = card(root);
        connection.addView(text("عنوان الراوتر", 14, true, DARK), matchWrap());
        routerIp = new EditText(this);
        routerIp.setSingleLine(true);
        routerIp.setText(DEFAULT_ROUTER);
        routerIp.setTextDirection(View.TEXT_DIRECTION_LTR);
        routerIp.setInputType(InputType.TYPE_CLASS_PHONE);
        routerIp.setPadding(dp(12), dp(8), dp(12), dp(8));
        connection.addView(routerIp, new LinearLayout.LayoutParams(-1, dp(48)));
        Button connect = button("اتصال وفتح صفحة الراوتر", true);
        connect.setOnClickListener(v -> connectToRouter());
        connection.addView(connect, matchWrap());
        statusView = text("الحالة: لم يتم الاتصال بعد", 13, false, MUTED);
        connection.addView(statusView, matchWrap());

        gap(root, 10);
        LinearLayout nav = card(root);
        nav.addView(text("اختصارات صفحات الراوتر", 15, true, DARK), matchWrap());
        addNavButton(nav, "قائمة أجهزة DHCP", () -> openRouterSection("DHCP server", "DHCP Client List"));
        addNavButton(nav, "الأجهزة اللاسلكية المتصلة", () -> openRouterSection("Wireless settings", "Connection Status"));
        addNavButton(nav, "تصفية MAC وجدولة الإنترنت", () -> openRouterSection("Security settings", "MAC Address Filter Settings"));
        addNavButton(nav, "ضبط وقت الراوتر", () -> openRouterSection("System tools", "Time Settings"));
        addNavButton(nav, "الرجوع في صفحات الراوتر", () -> {
            if (webView != null && webView.canGoBack()) webView.goBack();
        });

        gap(root, 10);
        LinearLayout schedule = card(root);
        schedule.addView(text("مساعد الجدولة: 4:00 مساءً إلى 12:00 منتصف الليل", 15, true, DARK), matchWrap());
        schedule.addView(text(
                "دليل W303R يوضح أن MAC Address Filter يدعم وضع Forbid only أو Permit only، ورقم سياسة، وعنوان MAC، ووقت البداية والنهاية، وأيام الأسبوع، وخيار Enable. " +
                "إذا كان المطلوب منع الجهاز خارج فترة 16:00–24:00، فالخطة المرشحة هي Forbid only بوقت 00:00–16:00 لكل جهاز، مع اختيار جميع الأيام. " +
                "هذا شرح للقاعدة وليس تطبيقاً تلقائياً لها؛ اختبر جهازاً واحداً أولاً، ولا تستخدم 00:00–00:00 لأنها موثقة كقاعدة تعمل طوال 24 ساعة.",
                13, false, MUTED), matchWrap());
        schedule.addView(text(
                "مهم: لا تخلط بين Wireless Access Control (منع الاتصال بالواي فاي) وبين MAC Address Filter ضمن Security settings (التحكم بوصول الجهاز إلى الإنترنت).",
                13, true, DARK), matchWrap());

        gap(root, 10);
        LinearLayout browserCard = card(root);
        browserCard.addView(text("واجهة الراوتر الأصلية", 15, true, DARK), matchWrap());
        pageView = text("لم تُفتح صفحة الراوتر بعد.", 12, false, MUTED);
        browserCard.addView(pageView, matchWrap());
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        browserCard.addView(webView, new LinearLayout.LayoutParams(-1, dp(480)));
        TextView webHint = text("سجّل الدخول داخل صفحة الراوتر نفسها. لا تكتب كلمة المرور في أي حقل داخل التطبيق خارج صفحة الإدارة المحلية.", 12, false, MUTED);
        browserCard.addView(webHint, matchWrap());

        gap(root, 10);
        LinearLayout tools = card(root);
        tools.addView(text("قراءة وتشخيص", 15, true, DARK), matchWrap());
        Button read = button("قراءة الأجهزة من الصفحة الحالية", false);
        read.setOnClickListener(v -> readDevicesFromPage());
        tools.addView(read, matchWrap());
        Button inspect = button("فحص حقول الصفحة الحالية", false);
        inspect.setOnClickListener(v -> inspectCurrentPage());
        tools.addView(inspect, matchWrap());
        Button copy = button("نسخ تقرير التشخيص", false);
        copy.setOnClickListener(v -> copyDiagnostic());
        tools.addView(copy, matchWrap());
        devicesView = text("قائمة الأجهزة ستظهر هنا بعد فتح صفحة DHCP Client List ثم الضغط على قراءة الأجهزة.", 12, false, DARK);
        devicesView.setTextIsSelectable(true);
        tools.addView(devicesView, matchWrap());
        diagnosticView = text("تقرير الصفحة: لم يتم الفحص بعد.", 11, false, MUTED);
        diagnosticView.setTextIsSelectable(true);
        tools.addView(diagnosticView, matchWrap());

        TextView footer = text("الإصدار الأول تشخيصي وآمن: لا يغيّر إعدادات الراوتر ولا يرسل أوامر حظر تلقائياً قبل التحقق من أسماء الحقول الفعلية لهذا الـ Firmware.", 12, true, MUTED);
        footer.setPadding(dp(4), dp(12), dp(4), dp(4));
        root.addView(footer, matchWrap());

        setContentView(outer);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSavePassword(false);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.addJavascriptInterface(new RouterBridge(), "TendaBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isAllowedRouterUri(uri)) return false;
                status("تم منع فتح عنوان خارج شبكة الراوتر المحلية.");
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (isAllowedRouterUri(uri)) return false;
                status("تم منع فتح عنوان خارج شبكة الراوتر المحلية.");
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isAllowedRouterUri(request.getUrl())) return null;
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageView.setText("الصفحة الحالية: " + url);
                status("تم تحميل صفحة الراوتر. إذا ظهرت شاشة الدخول، سجّل الدخول من داخلها.");
                handler.postDelayed(() -> inspectCurrentPage(), 500);
            }
        });
    }

    private void connectToRouter() {
        String ip = routerIp.getText().toString().trim();
        if (!isPrivateIpv4(ip)) {
            toast("أدخل عنوان IPv4 محلياً صحيحاً، مثل 192.168.0.1");
            return;
        }
        activeHost = ip;
        lastDiagnostic = "";
        lastDevices = "";
        devicesView.setText("لم تتم قراءة الأجهزة بعد.");
        diagnosticView.setText("سيظهر تقرير الصفحة بعد تحميل واجهة الراوتر.");
        webView.loadUrl("http://" + activeHost + "/");
    }

    private boolean isAllowedRouterUri(Uri uri) {
        if (uri == null || !"http".equalsIgnoreCase(uri.getScheme())) return false;
        return activeHost.equals(uri.getHost());
    }

    private boolean isPrivateIpv4(String ip) {
        if (ip == null || !ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) return false;
        try {
            byte[] b = InetAddress.getByName(ip).getAddress();
            if (b.length != 4) return false;
            int a = b[0] & 255, second = b[1] & 255;
            return a == 10 || (a == 172 && second >= 16 && second <= 31)
                    || (a == 192 && second == 168) || (a == 169 && second == 254);
        } catch (Exception e) {
            return false;
        }
    }

    private void openRouterSection(String parentLabel, String childLabel) {
        if (webView.getUrl() == null) {
            toast("اتصل بالراوتر وسجّل الدخول أولاً.");
            return;
        }
        clickRouterLabel(parentLabel, parentFound -> handler.postDelayed(() ->
                clickRouterLabel(childLabel, childFound -> {
                    if (parentFound && childFound) status("تم فتح " + parentLabel + " ثم " + childLabel + ".");
                    else if (parentFound) status("فُتح القسم الرئيسي، لكن لم أجد " + childLabel + ". افتحه يدوياً ثم أعد التشخيص.");
                    else status("لم أجد رابط " + parentLabel + ". تأكد من تسجيل الدخول أو افتح القسم يدوياً.");
                }), 1200));
        status("أحاول فتح: " + parentLabel + " ثم " + childLabel + " — تأكد من تسجيل الدخول.");
    }

    private interface BooleanResult { void onResult(boolean value); }

    private void clickRouterLabel(String label, BooleanResult callback) {
        String script = "(function(){" +
                "function docs(){var a=[document];for(var i=0;i<a.length;i++){try{var fs=a[i].querySelectorAll('frame,iframe');for(var j=0;j<fs.length;j++){try{var d=fs[j].contentDocument;if(d&&a.indexOf(d)<0)a.push(d);}catch(e){}}}catch(e){}}return a;}" +
                "var ds=docs(),q=" + JSONObject.quote(label.toLowerCase(Locale.ROOT)) + ";" +
                "for(var i=0;i<ds.length;i++){var es=ds[i].querySelectorAll('a,button,input[type=button],input[type=submit],area');for(var j=0;j<es.length;j++){var e=es[j],t=((e.innerText||e.textContent||e.value||e.title||'')+'').replace(/\\s+/g,' ').trim().toLowerCase();if(t===q||t.indexOf(q)>=0){e.click();return true;}}}return false;})()";
        webView.evaluateJavascript(script, result -> callback.onResult("true".equals(result)));
    }

    private void readDevicesFromPage() {
        String js = "(function(){" +
                "function docs(){var a=[document];for(var i=0;i<a.length;i++){try{var fs=a[i].querySelectorAll('frame,iframe');for(var j=0;j<fs.length;j++){try{var d=fs[j].contentDocument;if(d&&a.indexOf(d)<0)a.push(d);}catch(e){}}}catch(e){}}return a;}" +
                "var out=[],seen={};var ds=docs();" +
                "for(var x=0;x<ds.length;x++){var ts=ds[x].querySelectorAll('table');for(var t=0;t<ts.length;t++){var rs=ts[t].querySelectorAll('tr');for(var r=0;r<rs.length;r++){var cells=Array.prototype.slice.call(rs[r].querySelectorAll('th,td')).map(function(c){return (c.innerText||c.textContent||'').replace(/\\s+/g,' ').trim();});var raw=cells.join(' | ');var m=raw.match(/[0-9a-f]{2}(?:[:-][0-9a-f]{2}){5}/i);if(!m)continue;var mac=m[0].replace(/-/g,':').toUpperCase();if(seen[mac])continue;seen[mac]=1;var ipm=raw.match(/(?:\\d{1,3}\\.){3}\\d{1,3}/);var lease='';for(var z=0;z<cells.length;z++){if(/^\\d{1,2}:\\d{2}:\\d{2}$/.test(cells[z])||/^\\d{1,2}\\s*(hour|day|minute)/i.test(cells[z]))lease=cells[z];}var name='';for(var z=0;z<cells.length;z++){var c=cells[z];if(!c||c===mac||c===(ipm?ipm[0]:'')||c===lease||/^(no\\.?|host name|ip address|mac address|lease time)$/i.test(c))continue;if(!name)name=c;}out.push({mac:mac,ip:ipm?ipm[0]:'',name:name,lease:lease,raw:raw});}}}TendaBridge.clients(JSON.stringify(out));return out.length;})()";
        webView.evaluateJavascript(js, null);
        status("أقرأ عناوين MAC من الجداول الظاهرة في صفحة الراوتر الحالية...");
    }

    private void inspectCurrentPage() {
        if (webView.getUrl() == null) return;
        String js = "(function(){" +
                "function docs(){var a=[document];for(var i=0;i<a.length;i++){try{var fs=a[i].querySelectorAll('frame,iframe');for(var j=0;j<fs.length;j++){try{var d=fs[j].contentDocument;if(d&&a.indexOf(d)<0)a.push(d);}catch(e){}}}catch(e){}}return a;}" +
                "function pathOnly(u){try{return new URL(u,location.href).pathname;}catch(e){return '';}}" +
                "var ds=docs(),o={url:pathOnly(location.href),title:document.title,frames:[],links:[],forms:[],tables:[]};" +
                "for(var x=0;x<ds.length;x++){var d=ds[x];o.frames.push(d.location?pathOnly(d.location.href):'frame');var as=d.querySelectorAll('a');for(var i=0;i<as.length&&o.links.length<120;i++){var a=as[i];var tx=((a.innerText||a.textContent||'')+'').replace(/\\s+/g,' ').trim();if(tx)o.links.push({text:tx,href:pathOnly(a.href||'')});}" +
                "var fs=d.querySelectorAll('form');for(var f=0;f<fs.length&&o.forms.length<30;f++){var fm=fs[f],ob={action:pathOnly(fm.action||''),method:fm.method||'',fields:[]};var cs=fm.querySelectorAll('input,select,textarea,button');for(var k=0;k<cs.length&&ob.fields.length<100;k++){var c=cs[k],typ=(c.type||'').toLowerCase(),nm=(c.name||'')+' '+(c.id||''),secret=typ==='password'||typ==='hidden'||/(pass|token|cookie|auth|session|key|csrf|stok)/i.test(nm),z={tag:c.tagName.toLowerCase(),type:typ,name:c.name||'',id:c.id||'',value:secret?'[محجوب]':(c.value||''),text:(c.innerText||c.textContent||'').trim()};if(c.tagName==='SELECT'){z.options=[];for(var n=0;n<c.options.length&&n<40;n++)z.options.push({text:c.options[n].text,value:c.options[n].value});}ob.fields.push(z);}o.forms.push(ob);}" +
                "var ts=d.querySelectorAll('table');for(var t=0;t<ts.length&&o.tables.length<30;t++){var tab=ts[t],headers=[];var hs=tab.querySelectorAll('th');for(var h=0;h<hs.length&&h<20;h++)headers.push((hs[h].innerText||hs[h].textContent||'').replace(/\\s+/g,' ').trim());o.tables.push({headers:headers,rowCount:tab.querySelectorAll('tr').length});}}" +
                "TendaBridge.diagnostic(JSON.stringify(o));return 'ok';})()";
        webView.evaluateJavascript(js, null);
    }

    private void copyDiagnostic() {
        String value = lastDiagnostic;
        if (value == null || value.trim().isEmpty()) {
            toast("لا يوجد تقرير بعد. اضغط فحص حقول الصفحة الحالية أولاً.");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Tenda W303 diagnostic", value));
        toast("تم نسخ تقرير التشخيص. يمكنك لصقه هنا في المحادثة.");
    }

    private void renderClients(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) {
                lastDevices = "لم أعثر على عناوين MAC في الجداول الحالية. افتح DHCP Client List ثم أعد القراءة.";
                devicesView.setText(lastDevices);
                return;
            }
            StringBuilder b = new StringBuilder();
            b.append("عدد السجلات التي عُثر عليها: ").append(arr.length())
                    .append("\nملاحظة: هذه سجلات DHCP وليست دليلاً قاطعاً على أن كل جهاز متصل الآن.\n\n");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                b.append(i + 1).append(". ").append(o.optString("name", "جهاز"))
                        .append("\n   IP: ").append(o.optString("ip", "غير متاح"))
                        .append("\n   MAC: ").append(o.optString("mac", ""))
                        .append("\n   مدة الإيجار: ").append(o.optString("lease", "غير متاحة"))
                        .append("\n\n");
            }
            lastDevices = b.toString();
            devicesView.setText(lastDevices);
            status("اكتملت قراءة سجلات DHCP. لم يتم تغيير أي إعداد.");
        } catch (JSONException e) {
            devicesView.setText("تعذر تحليل قائمة الأجهزة. أرسل تقرير التشخيص.");
        }
    }

    private void renderDiagnostic(String json) {
        lastDiagnostic = json;
        String preview = json;
        if (preview.length() > 7000) preview = preview.substring(0, 7000) + "\n... تم اختصار العرض؛ التقرير الكامل جاهز للنسخ.";
        diagnosticView.setText(preview);
        status("تم فحص الصفحة الحالية. انسخ التقرير وأرسله لتحديد حقول Firmware بدقة.");
    }

    private void status(String value) {
        if (statusView != null) statusView.setText("الحالة: " + value);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private class RouterBridge {
        @JavascriptInterface
        public void clients(String json) {
            runOnUiThread(() -> renderClients(json));
        }

        @JavascriptInterface
        public void diagnostic(String json) {
            runOnUiThread(() -> renderDiagnostic(json));
        }

        @JavascriptInterface
        public void navResult(boolean parentFound, boolean childFound, String labels) {
            runOnUiThread(() -> {
                if (parentFound && childFound) status("تم الضغط على " + labels + ".");
                else if (parentFound) status("تم فتح القسم الرئيسي، لكن لم أجد الرابط الفرعي " + labels + ". افتحه يدوياً ثم أعد التشخيص.");
                else status("لم أجد رابط " + labels + ". تأكد من تسجيل الدخول أو افتح القسم يدوياً.");
            });
        }
    }

    private void addNavButton(LinearLayout parent, String label, Runnable action) {
        Button b = button(label, false);
        b.setOnClickListener(v -> action.run());
        parent.addView(b, matchWrap());
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackgroundColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), BORDER);
        box.setBackground(bg);
        parent.addView(box, matchWrap());
        return box;
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.RIGHT);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        t.setPadding(0, dp(3), 0, dp(5));
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(primary ? Color.WHITE : DARK);
        b.setPadding(dp(8), dp(4), dp(8), dp(4));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(primary ? ORANGE : Color.rgb(245, 246, 248));
        bg.setCornerRadius(dp(9));
        if (!primary) bg.setStroke(dp(1), BORDER);
        b.setBackground(bg);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void gap(LinearLayout parent, int h) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("TendaBridge");
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        super.onDestroy();
    }
}
