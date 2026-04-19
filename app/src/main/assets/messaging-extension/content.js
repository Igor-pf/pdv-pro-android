(function() {
    console.log("GeckoView Bridge: Injecting Native Interfaces...");

    const callNative = (bridge, method, params = []) => {
        console.log(`GeckoView Bridge: Calling ${bridge}.${method}`, params);
        return browser.runtime.sendMessage({
            bridge: bridge,
            method: method,
            params: params
        });
    };

    // Define the bridges
    const bridges = {
        AndroidApp: {
            showNotification: (title, body) => callNative('AndroidApp', 'showNotification', [title, body]),
            print: () => callNative('AndroidApp', 'print'),
            printHtml: (html) => callNative('AndroidApp', 'printHtml', [html]),
            scanQRCode: () => callNative('AndroidApp', 'scanQRCode')
        },
        AndroidPrinter: {
            imprimir: (texto) => callNative('AndroidPrinter', 'imprimir', [texto]),
            printList: (json) => callNative('AndroidPrinter', 'printList', [json])
        }
    };

    // Inject window.AndroidApp and window.AndroidPrinter
    window.AndroidApp = bridges.AndroidApp;
    window.AndroidPrinter = bridges.AndroidPrinter;

    // ALIAS: window.Android for legacy compatibility (Very common in PDV)
    window.Android = {
        ...bridges.AndroidApp,
        ...bridges.AndroidPrinter
    };

    console.log("GeckoView Bridge: window.AndroidApp, window.AndroidPrinter, and window.Android are READY.");
})();
