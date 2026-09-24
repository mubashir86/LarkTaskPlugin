package app.com.brd.plugin.lark.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class StatusBadgeCellRenderer : TableCellRenderer {

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val statusText = value?.toString() ?: ""
        return BadgeLabel(statusText)
    }

    private class BadgeLabel(
        private val statusText: String
    ) : JLabel(statusText, CENTER) {

        init {
            font = font.deriveFont(Font.BOLD, 11f)
            border = JBUI.Borders.empty(2, 8)
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val bg = getBadgeColor(statusText)
            val fg = Color.WHITE

            val width = size.width
            val height = size.height

            // Center badge box
            val badgeWidth = Math.min(width - 8, 95)
            val x = (width - badgeWidth) / 2
            val y = (height - 18) / 2

            g2.color = bg
            g2.fillRoundRect(x, y, badgeWidth, 18, 10, 10)

            g2.color = fg
            val fm = g2.fontMetrics
            val stringWidth = fm.stringWidth(statusText)
            val stringX = (width - stringWidth) / 2
            val stringY = (height - fm.height) / 2 + fm.ascent

            g2.font = font
            g2.drawString(statusText, stringX, stringY)
            g2.dispose()
        }

        private fun getBadgeColor(status: String): Color {
            return when (status.lowercase().trim()) {
                "problem" -> JBColor(Color(180, 50, 50), Color(160, 60, 60))
                "in progress" -> JBColor(Color(30, 90, 180), Color(40, 110, 200))
                "done" -> JBColor(Color(35, 130, 70), Color(45, 150, 80))
                "to do" -> JBColor(Color(100, 110, 120), Color(110, 120, 130))
                else -> JBColor(Color(80, 100, 120), Color(90, 110, 130))
            }
        }
    }
}
