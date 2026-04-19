(function() {
    console.log("GeckoView Content Script: Injecting Android Bridges...");

    const callNative = (bridge, method, params = []) => {
        return browser.runtime.sendMessage({
            bridge: bridge,
            method: method,
            params: params
        });
    };

    // Inject window.AndroidApp
    window.AndroidApp = {
        showNotification: (title, body) => callNative('AndroidApp', 'showNotification', [title, body]),
        print: () => callNative('AndroidApp', 'print'),
        printHtml: (html) => callNative('AndroidApp', 'printHtml', [html]),
        scanQRCode: () => callNative('AndroidApp', 'scanQRCode')
    };

    // Inject window.AndroidPrinter
    window.AndroidPrinter = {
        imprimir: (texto) => callNative('AndroidPrinter', 'imprimir', [texto]),
        printList: (json) => callNative('AndroidPrinter', 'printList', [json])
    };

    console.log("GeckoView Content Script: Bridges Injected Successfully.");
})();
