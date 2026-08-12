// ==========================================================
// native-bell.js — Android native exact-alarm bridge
// ==========================================================
(function () {
    "use strict";

    function getPlugin() {
        try {
            return window.Capacitor?.Plugins?.NativeBell || null;
        } catch (_) { return null; }
    }

    const native = !!(window.Capacitor &&
        window.Capacitor.isNativePlatform &&
        window.Capacitor.isNativePlatform());

    if (!native) {
        window.NativeBell = {
            isNative: function () { return false; },
            enable: async function () {},
            disable: async function () {},
            test: async function () {}
        };
        return;
    }

    async function enable() {
        const plugin = getPlugin();
        if (!plugin) {
            console.error("NativeBell plugin भेटिएन।");
            return;
        }
        try {
            await plugin.enable({
                periods: (typeof timePeriods !== "undefined" ? timePeriods : []),
                dismissalHour: 16,
                dismissalMinute: 0
            });
        } catch (err) {
            console.error("NativeBell enable failed:", err);
        }
    }

    async function disable() {
        const plugin = getPlugin();
        if (!plugin) return;
        try { await plugin.disable(); }
        catch (err) { console.error("NativeBell disable failed:", err); }
    }

    async function test() {
        const plugin = getPlugin();
        if (!plugin) return;
        try { await plugin.test(); }
        catch (err) { console.error("NativeBell test failed:", err); }
    }

    window.NativeBell = {
        isNative: function () { return native && !!getPlugin(); },
        enable: enable,
        disable: disable,
        test: test
    };

    document.addEventListener("resume", function () {
        try {
            if (window.isBackgroundNotifyEnabled) enable();
        } catch (_) {}
    }, false);
})();
