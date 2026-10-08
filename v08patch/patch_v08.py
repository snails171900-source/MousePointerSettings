from pathlib import Path
root=Path('MousePointerSettings-v0.8-system-package')
# Gradle version
p=root/'app/build.gradle'; s=p.read_text(); s=s.replace('versionCode 7','versionCode 8').replace("versionName '0.7.0'","versionName '0.8.0'").replace('v0.7 Play-Protect-safe mode','v0.8 Play-Protect-safe mode'); p.write_text(s)
(root/'VERSION').write_text('0.8.0\n')
for rel in ['README.md','BUILD_INFO.md','docs/SAFETY.md','app/proguard-rules.pro']:
    p=root/rel
    if p.exists(): p.write_text(p.read_text(errors='ignore').replace('v0.7','v0.8').replace('0.7.0','0.8.0'))

# Native pointer forcing + system bar insets
p=root/'app/src/main/java/com/pointer/settings/actual/MainActivity.java'; s=p.read_text()
s=s.replace('import android.view.PointerIcon;','import android.view.PointerIcon;\nimport android.view.MotionEvent;\nimport android.view.WindowInsets;\nimport android.widget.FrameLayout;')
s=s.replace(' * v0.7 Play-Protect-safe build.',' * v0.8 Play-Protect-safe build.')
s=s.replace('    private WebView web;','    private PointerWebView web;')
s=s.replace('        web = new WebView(this);','        web = new PointerWebView(this);')
s=s.replace('        setContentView(web);\n        web.loadUrl("file:///android_asset/index.html");','''        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(244, 246, 248));
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        web.loadUrl("file:///android_asset/index.html");''')
s=s.replace('                web.setPointerIcon(PointerIcon.create(a.bitmap, hx, hy));','                web.setForcedPointerIcon(PointerIcon.create(a.bitmap, hx, hy));')
s=s.replace('            try { web.setPointerIcon(PointerIcon.getSystemIcon(this, PointerIcon.TYPE_DEFAULT)); }\n            catch (Throwable ignored) { }','            try { web.clearForcedPointerIcon(); }\n            catch (Throwable ignored) { }')
marker='    private final class Bridge {'
sub='''    /** WebView normally resolves its own link/text icons. Keep the selected theme authoritative while applied. */
    private static final class PointerWebView extends WebView {
        private PointerIcon forcedPointerIcon;
        PointerWebView(Context context) { super(context); }
        void setForcedPointerIcon(PointerIcon icon) { forcedPointerIcon = icon; super.setPointerIcon(icon); }
        void clearForcedPointerIcon() { forcedPointerIcon = null; super.setPointerIcon(null); }
        @Override public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
            return forcedPointerIcon != null ? forcedPointerIcon : super.onResolvePointerIcon(event, pointerIndex);
        }
    }

'''
if sub not in s: s=s.replace(marker,sub+marker)
p.write_text(s)

p=root/'app/src/main/java/com/pointer/settings/actual/PointerApplication.java'; p.write_text(p.read_text().replace('v0.7 startup cleanup','v0.8 startup cleanup'))

# Make four theme silhouettes visibly distinct
p=root/'app/src/main/java/com/pointer/settings/actual/CursorThemeRenderer.java'; s=p.read_text()
s=s.replace('Paint body = paint(bodyColor, Paint.Style.FILL, theme.equals("31") || theme.equals("95") ? 0 : 1f);\n        Paint border = paint(borderColor, Paint.Style.STROKE, 0f);','Paint body = paint(bodyColor, Paint.Style.FILL, theme.equals("7") ? 1.2f : theme.equals("10") ? 0.7f : 0f);\n        Paint border = paint(borderColor, Paint.Style.STROKE, 0f);\n        if (theme.equals("31")) { body.setAntiAlias(false); border.setAntiAlias(false); }')
s=s.replace('border.setStrokeWidth(theme.equals("31") ? 4f : theme.equals("95") ? 4.5f : theme.equals("7") ? 3.2f : 3.5f);','border.setStrokeWidth(theme.equals("31") ? 5f : theme.equals("95") ? 4.2f : theme.equals("7") ? 3.0f : 3.4f);')
s=s.replace('''        if (theme.equals("31")) {
            p.moveTo(7,7); p.lineTo(7,69); p.lineTo(22,54); p.lineTo(35,84); p.lineTo(48,78);
            p.lineTo(35,50); p.lineTo(62,50); p.close();
        } else if (theme.equals("95")) {
            p.moveTo(6,5); p.lineTo(6,72); p.lineTo(23,56); p.lineTo(37,87); p.lineTo(50,81);
            p.lineTo(37,52); p.lineTo(66,52); p.close();
        } else if (theme.equals("7")) {
            p.moveTo(8,5); p.lineTo(10,70); p.quadTo(10,75,14,72); p.lineTo(27,59);
            p.lineTo(41,88); p.lineTo(53,82); p.lineTo(39,54); p.lineTo(65,53); p.quadTo(69,52,66,49); p.close();
        } else {
            p.moveTo(7,5); p.lineTo(9,72); p.lineTo(27,56); p.lineTo(42,87); p.lineTo(54,81);
            p.lineTo(40,52); p.lineTo(67,52); p.close();
        }''','''        if (theme.equals("31")) {
            p.moveTo(9,8); p.lineTo(9,64); p.lineTo(23,51); p.lineTo(36,80); p.lineTo(47,75); p.lineTo(35,48); p.lineTo(59,48); p.close();
        } else if (theme.equals("95")) {
            p.moveTo(6,5); p.lineTo(6,73); p.lineTo(24,56); p.lineTo(38,88); p.lineTo(51,81); p.lineTo(37,52); p.lineTo(67,52); p.close();
        } else if (theme.equals("7")) {
            p.moveTo(9,6); p.lineTo(11,68); p.quadTo(11,74,15,71); p.lineTo(28,59); p.lineTo(41,87); p.lineTo(52,81); p.lineTo(39,54); p.lineTo(64,53); p.quadTo(69,52,65,48); p.close();
        } else {
            p.moveTo(7,5); p.lineTo(9,71); p.lineTo(27,55); p.lineTo(42,86); p.lineTo(54,80); p.lineTo(40,51); p.lineTo(67,51); p.close();
        }''')
p.write_text(s)

# Responsive width/height correction without scrolling
p=root/'app/src/main/assets/index.html'; s=p.read_text().replace('민감 권한 없음 · v0.7','민감 권한 없음 · v0.8')
fix='''<style id="v08fix">
.main{grid-template-columns:minmax(0,34fr) minmax(0,66fr)}
@media(max-width:430px){
.app{grid-template-rows:36px minmax(0,1fr) 38px;gap:4px;padding:4px}.top{padding:3px 7px;gap:4px}.title{font-size:14px}.top .sub{font-size:7px;max-width:84px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.status{font-size:7px;max-width:110px;padding:3px 5px;overflow:hidden;text-overflow:ellipsis}.main{grid-template-columns:minmax(0,30fr) minmax(0,70fr);gap:4px}.preview{padding:4px}.cardTitle{font-size:10px}.sub{font-size:6.5px}.stage{margin-top:3px}.stage img{width:min(74%,104px);max-height:62%}.previewMeta{font-size:7px;margin-top:3px}.settings{grid-template-rows:1.82fr .52fr .70fr .84fr .98fr 40px}.section{padding:4px 5px}.secTitle{font-size:9px;margin-bottom:2px}.num{width:15px;height:15px;font-size:7px}.formats{gap:3px;height:calc(100% - 17px)}.format{grid-template-columns:12px minmax(0,1fr) repeat(3,16px);gap:1px;padding:2px 3px;min-width:0}.format b{font-size:6.9px;overflow:hidden;text-overflow:ellipsis}.mini{width:16px;height:16px}.row{grid-template-columns:49px minmax(0,1fr) 35px;gap:3px}.row label,.tiny{font-size:6.8px}.value{font-size:7.5px}.sw{width:15px;height:15px}.presets .sw:nth-child(n+5){display:none}.colorInput{width:20px;height:18px}.switchRow{font-size:6.8px}.radioCard{font-size:6px;padding:3px}.radioCard b{font-size:6.8px}.chip{font-size:5.7px;padding:2px 3px}.experimental .chip:nth-child(n+3){display:none}.actions{gap:3px;padding:4px}.btn{font-size:6.7px;padding:4px 2px}.footer{padding:3px 6px;gap:4px}.limit{font-size:6.3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.limit br{display:none}.limit b{font-size:6.7px}.diagBtn{font-size:6.7px;padding:4px 5px}.toast{bottom:48px;font-size:8px}}
</style>'''
s=s.replace('</head>',fix+'</head>')
p.write_text(s)
