package app.com.brd.plugin.lark.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic

fun interface LarkAppSettingsNotifier {

    fun onSettingsChanged()

    companion object {
        @JvmStatic
        val LARK_SETTINGS_TOPIC: Topic<LarkAppSettingsNotifier> =
            Topic.create("Lark App Settings Changed", LarkAppSettingsNotifier::class.java)

        fun notifySettingsChanged() {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(LARK_SETTINGS_TOPIC)
                .onSettingsChanged()
        }
    }
}
