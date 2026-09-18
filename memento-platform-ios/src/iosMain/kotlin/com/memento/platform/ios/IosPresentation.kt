package com.memento.platform.ios

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * The top-most view controller currently on screen: the root controller with any modally presented
 * controllers walked to the front. Used as the presenter for system pickers and share sheets.
 */
fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
