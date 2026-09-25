package com.tss.lark

import com.tss.lark.model.LarkFieldInfo
import com.tss.lark.model.LarkRecord
import com.tss.lark.model.LarkTableInfo
import com.tss.lark.service.LarkAuthResult
import com.tss.lark.service.LarkBitableApiService
import com.tss.lark.settings.LarkAppSettingsConfigurable
import com.tss.lark.settings.LarkAppSettingsNotifier
import com.tss.lark.settings.LarkAppSettingsState
import com.tss.lark.ui.BaseUrlHelpDialog
import com.tss.lark.ui.DynamicCreateRecordDialog
import com.tss.lark.ui.TokenHelpDialog
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

class LarkToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val larkPanel = LarkTaskPanel(project)
        val content = ContentFactory.getInstance().createContent(larkPanel.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

class LarkTaskPanel(private val project: Project) {

    private val rootCardLayout = CardLayout()
    private val rootPanel = JPanel(rootCardLayout)

    private val apiService = LarkBitableApiService.getInstance()

    // Step 1 components
    private var step1BaseUrl: String = ""
    private val step1UrlInput = JBTextField()
    private val step1ErrorLabel = JBLabel().apply {
        foreground = JBColor(Color(180, 50, 50), Color(200, 60, 60))
        font = font.deriveFont(Font.PLAIN, 12f)
        isVisible = false
    }
    private val step1CancelBtn = JButton("Cancel (Return to Dashboard)", AllIcons.Actions.Cancel).apply {
        isVisible = false
    }

    // Step 2 components
    private val step2TokenInput = JBPasswordField()
    private val step2FeedbackLabel = JBLabel().apply { font = font.deriveFont(Font.PLAIN, 12f) }
    private val step2FeedbackPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(6)
        isVisible = false
    }
    private val step2CancelBtn = JButton("Cancel (Return to Dashboard)", AllIcons.Actions.Cancel).apply {
        isVisible = false
    }
    private val copyErrorBtn = JButton("Copy Error Log", AllIcons.Actions.Copy).apply { isVisible = false }
    private val viewErrorDetailsBtn = JButton("View Response Details", AllIcons.Actions.ShowCode).apply { isVisible = false }
    private var lastAuthResult: LarkAuthResult? = null

    // Success Card components
    private val successBaseTokenLabel = JBLabel()
    private val successTableIdLabel = JBLabel()

    // Dashboard State
    private var currentTables = listOf<LarkTableInfo>()
    private var activeTableId: String = ""
    private var currentFields = listOf<LarkFieldInfo>()
    private var currentRecords = listOf<LarkRecord>()
    private var filteredRecords = listOf<LarkRecord>()
    private var selectedRecord: LarkRecord? = null

    private var autoRefreshTimer: javax.swing.Timer? = null

    // Dashboard View components
    private val dashboardPanel = JPanel(BorderLayout())

    // Base & Table Selector Controls
    private val baseSelectorCombo = JComboBox<String>()
    private val tableSelectorCombo = JComboBox<String>()
    private val autoRefreshCombo = JComboBox(arrayOf("Auto Refresh: Off", "Every 1 Min", "Every 5 Min", "Every 10 Min"))
    private val searchField = SearchTextField(false)
    private val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)
    private val createButton = JButton("+ Add Record", AllIcons.General.Add)
    private val addBaseButton = JButton("+ Add Base Sheet", AllIcons.General.Add)
    private val disconnectButton = JButton("Disconnect", AllIcons.Actions.Exit)

    // Table
    private val tableModel = DefaultTableModel()
    private val table = JBTable(tableModel)

    // Status Label
    private val paginationLabel = JBLabel("Ready")

    // Detail Panel
    private val detailCardLayout = CardLayout()
    private val detailContainer = JPanel(detailCardLayout)

    private val emptyDetailPanel = JPanel(BorderLayout()).apply {
        val label = JBLabel("Select a record to view details", SwingConstants.CENTER).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            font = font.deriveFont(Font.PLAIN, 13f)
        }
        add(label, BorderLayout.CENTER)
    }

    private val activeDetailPanel = JPanel(BorderLayout())
    private val detailTitleLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD, 15f) }
    private val detailKeyLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD, 12f) }
    private val detailFieldsBox = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8)
    }

    init {
        setupDashboardUI()

        rootPanel.add(createStep1View(), "AUTH_STEP_1")
        rootPanel.add(createStep2View(), "AUTH_STEP_2")
        rootPanel.add(createSuccessView(), "AUTH_SUCCESS")
        rootPanel.add(dashboardPanel, "DASHBOARD_CARD")

        checkAuthState()

        // Subscribe to settings changes
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(LarkAppSettingsNotifier.LARK_SETTINGS_TOPIC, LarkAppSettingsNotifier {
                checkAuthState()
            })
    }

    private fun checkAuthState() {
        val connected = apiService.isConnected()
        step1CancelBtn.isVisible = connected
        step2CancelBtn.isVisible = connected

        if (connected) {
            updateBaseSelectorDropdown()
            rootCardLayout.show(rootPanel, "DASHBOARD_CARD")
            loadDashboardData()
        } else {
            rootCardLayout.show(rootPanel, "AUTH_STEP_1")
        }
    }

    private fun createStep1View(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(16)

        val centerBox = JBPanel<JBPanel<*>>()
        centerBox.layout = BoxLayout(centerBox, BoxLayout.Y_AXIS)
        centerBox.alignmentX = JComponent.CENTER_ALIGNMENT

        val titleLabel = JBLabel("Connect to Lark base", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 18f)
            alignmentX = JComponent.CENTER_ALIGNMENT
        }

        val subtitleLabel = JBLabel("<html><center>Connect your Lark Base to manage records and tasks directly inside Android Studio.</center></html>", SwingConstants.CENTER).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            alignmentX = JComponent.CENTER_ALIGNMENT
        }

        val formCard = JPanel()
        formCard.layout = BoxLayout(formCard, BoxLayout.Y_AXIS)
        formCard.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(),
            JBUI.Borders.empty(16)
        )
        formCard.maximumSize = Dimension(520, 320)

        val urlLabelRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        val urlLabel = JBLabel("Lark Base URL:").apply { font = font.deriveFont(Font.BOLD) }
        val whereFindLink = JButton("Where do I find this?", AllIcons.Actions.Help).apply {
            isContentAreaFilled = false
            isBorderPainted = false
            addActionListener { BaseUrlHelpDialog(project).show() }
        }
        urlLabelRow.add(urlLabel)
        urlLabelRow.add(Box.createHorizontalStrut(8))
        urlLabelRow.add(whereFindLink)

        step1UrlInput.emptyText.text = "Paste your Lark Base URL"
        step1UrlInput.maximumSize = Dimension(480, 32)
        step1UrlInput.preferredSize = Dimension(480, 32)

        val helpBox = JPanel()
        helpBox.layout = BoxLayout(helpBox, BoxLayout.Y_AXIS)
        helpBox.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            JBUI.Borders.empty(8, 0, 0, 0)
        )

        val helpHeading = JBLabel("What is a Lark Base URL?").apply { font = font.deriveFont(Font.BOLD, 12f) }
        val helpText = JBLabel("<html>A Lark Base URL is the web link to the specific Base you want to connect to.<br/><b>1.</b> Open Lark in your browser.<br/><b>2.</b> Open the Base you want to use.<br/><b>3.</b> Copy the URL from your browser's address bar.<br/><b>4.</b> Paste it into this field.</html>").apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

        helpBox.add(helpHeading)
        helpBox.add(Box.createVerticalStrut(4))
        helpBox.add(helpText)

        val continueBtn = JButton("Continue ->", AllIcons.Actions.Forward).apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }

        step1UrlInput.addActionListener {
            continueBtn.doClick()
        }

        step1CancelBtn.addActionListener {
            if (apiService.isConnected()) {
                rootCardLayout.show(rootPanel, "DASHBOARD_CARD")
            }
        }

        continueBtn.addActionListener {
            val url = step1UrlInput.text.trim()
            val validationError = apiService.validateUrl(url)
            if (validationError != null) {
                step1ErrorLabel.text = "❌ $validationError"
                step1ErrorLabel.isVisible = true
            } else {
                step1ErrorLabel.isVisible = false
                step1BaseUrl = url
                if (String(step2TokenInput.password).isBlank()) {
                    val savedToken = LarkAppSettingsState.getInstance().userToken
                    if (savedToken.isNotBlank()) {
                        step2TokenInput.text = savedToken
                    }
                }
                step2CancelBtn.isVisible = apiService.isConnected()
                rootCardLayout.show(rootPanel, "AUTH_STEP_2")
            }
        }

        val btnRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        btnRow.add(continueBtn)
        btnRow.add(step1CancelBtn)

        formCard.add(urlLabelRow)
        formCard.add(Box.createVerticalStrut(4))
        formCard.add(step1UrlInput)
        formCard.add(Box.createVerticalStrut(4))
        formCard.add(step1ErrorLabel)
        formCard.add(Box.createVerticalStrut(12))
        formCard.add(helpBox)
        formCard.add(Box.createVerticalStrut(12))
        formCard.add(btnRow)

        centerBox.add(Box.createVerticalGlue())
        centerBox.add(titleLabel)
        centerBox.add(Box.createVerticalStrut(8))
        centerBox.add(subtitleLabel)
        centerBox.add(Box.createVerticalStrut(16))
        centerBox.add(formCard)
        centerBox.add(Box.createVerticalGlue())

        val scroll = JBScrollPane(centerBox).apply {
            border = null
        }

        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    private fun createStep2View(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(16)

        val centerBox = JBPanel<JBPanel<*>>()
        centerBox.layout = BoxLayout(centerBox, BoxLayout.Y_AXIS)
        centerBox.alignmentX = JComponent.CENTER_ALIGNMENT

        val titleLabel = JBLabel("Authorize Lark access", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 18f)
            alignmentX = JComponent.CENTER_ALIGNMENT
        }

        val subtitleLabel = JBLabel("<html><center>To connect to your Lark Base, enter the authentication token required by this plugin.</center></html>", SwingConstants.CENTER).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            alignmentX = JComponent.CENTER_ALIGNMENT
        }

        val formCard = JPanel()
        formCard.layout = BoxLayout(formCard, BoxLayout.Y_AXIS)
        formCard.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(),
            JBUI.Borders.empty(16)
        )
        formCard.maximumSize = Dimension(520, 360)

        val tokenLabel = JBLabel("Personal access token:").apply { font = font.deriveFont(Font.BOLD) }
        step2TokenInput.emptyText.text = "pat_xxxxxxxxxxxxxxxxxxxxxxxx"
        step2TokenInput.maximumSize = Dimension(480, 32)
        step2TokenInput.preferredSize = Dimension(480, 32)

        val guideBox = JPanel()
        guideBox.layout = BoxLayout(guideBox, BoxLayout.Y_AXIS)
        guideBox.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            JBUI.Borders.empty(8, 0, 0, 0)
        )

        val guideHeading = JBLabel("Where do I get my token?").apply { font = font.deriveFont(Font.BOLD, 12f) }
        val guideText = JBLabel("<html><b>1.</b> Open Lark Developer Console (open.larksuite.com/app).<br/>" +
                "<b>2.</b> Select your App, go to <b>Permissions & Scopes</b>, and add <code>bitable:app</code>.<br/>" +
                "<b>3.</b> Copy your <b>Tenant Access Token</b> (<code>t-...</code>) or <b>User Access Token</b> (<code>u-...</code>) from Test Notes / API Explorer and paste it above.<br/>" +
                "<i>💡 Note: If Personal/User Token gives 400 error (e.g. App is 'Pending release'), copy a Tenant Access Token (t-...).</i></html>").apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

        guideBox.add(guideHeading)
        guideBox.add(Box.createVerticalStrut(4))
        guideBox.add(guideText)

        val backBtn = JButton("<- Back", AllIcons.Actions.Back)
        val tokenHelpBtn = JButton("How to Get a Token?", AllIcons.Actions.Help)
        val testConnectBtn = JButton("Connect & Test Workspace", AllIcons.Actions.Execute).apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }

        val feedbackTextRow = JPanel(FlowLayout(FlowLayout.CENTER))
        feedbackTextRow.add(step2FeedbackLabel)

        val feedbackActionRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 2))
        feedbackActionRow.add(copyErrorBtn)
        feedbackActionRow.add(viewErrorDetailsBtn)

        step2FeedbackPanel.add(feedbackTextRow)
        step2FeedbackPanel.add(feedbackActionRow)

        step2TokenInput.addActionListener {
            testConnectBtn.doClick()
        }

        backBtn.addActionListener {
            rootCardLayout.show(rootPanel, "AUTH_STEP_1")
        }

        step2CancelBtn.addActionListener {
            if (apiService.isConnected()) {
                rootCardLayout.show(rootPanel, "DASHBOARD_CARD")
            }
        }

        tokenHelpBtn.addActionListener {
            TokenHelpDialog(project).show()
        }

        copyErrorBtn.addActionListener {
            lastAuthResult?.let { res ->
                val logText = if (res.detailLog.isNotBlank()) res.detailLog else res.message
                val selection = StringSelection(logText)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                Messages.showInfoMessage(project, "Error log copied to system clipboard!", "Lark Connector")
            }
        }

        viewErrorDetailsBtn.addActionListener {
            lastAuthResult?.let { res ->
                val details = buildString {
                    append("HTTP Status: ").append(res.httpCode).append("\n\n")
                    append("Message:\n").append(res.message).append("\n\n")
                    if (res.rawResponseBody.isNotBlank()) {
                        append("Raw Response Body:\n").append(res.rawResponseBody).append("\n\n")
                    }
                    if (res.detailLog.isNotBlank()) {
                        append("Detail Request Log:\n").append(res.detailLog)
                    }
                }
                Messages.showMultilineInputDialog(project, "Lark Connection Diagnostics Log", "Lark API Response Details", details, null, null)
            }
        }

        testConnectBtn.addActionListener {
            val token = String(step2TokenInput.password).trim()
            val state = LarkAppSettingsState.getInstance()

            testConnectBtn.isEnabled = false
            testConnectBtn.text = "Testing connection..."
            step2FeedbackLabel.text = "⏳ Connecting to Lark Base API..."
            step2FeedbackLabel.foreground = JBUI.CurrentTheme.Label.foreground()
            step2FeedbackPanel.isVisible = true
            copyErrorBtn.isVisible = false
            viewErrorDetailsBtn.isVisible = false

            ApplicationManager.getApplication().executeOnPooledThread {
                val testResult = apiService.testConnection(step1BaseUrl, token, state.appId, state.appSecret)

                SwingUtilities.invokeLater {
                    testConnectBtn.isEnabled = true
                    testConnectBtn.text = "Connect & Test Workspace"
                    lastAuthResult = testResult

                    if (testResult.isSuccess) {
                        showStep2Feedback("✅ Connection successful!", true)
                        successBaseTokenLabel.text = testResult.appToken
                        successTableIdLabel.text = testResult.tableId
                        rootCardLayout.show(rootPanel, "AUTH_SUCCESS")
                    } else {
                        showStep2Feedback("❌ ${testResult.message}", false)
                        copyErrorBtn.isVisible = true
                        viewErrorDetailsBtn.isVisible = true
                    }
                }
            }
        }

        val primaryBtnRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        primaryBtnRow.add(testConnectBtn)

        val secondaryBtnRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        secondaryBtnRow.add(backBtn)
        secondaryBtnRow.add(tokenHelpBtn)
        secondaryBtnRow.add(step2CancelBtn)

        formCard.add(tokenLabel)
        formCard.add(Box.createVerticalStrut(4))
        formCard.add(step2TokenInput)
        formCard.add(Box.createVerticalStrut(12))
        formCard.add(guideBox)
        formCard.add(Box.createVerticalStrut(12))
        formCard.add(primaryBtnRow)
        formCard.add(Box.createVerticalStrut(4))
        formCard.add(secondaryBtnRow)
        formCard.add(Box.createVerticalStrut(8))
        formCard.add(step2FeedbackPanel)

        centerBox.add(Box.createVerticalGlue())
        centerBox.add(titleLabel)
        centerBox.add(Box.createVerticalStrut(8))
        centerBox.add(subtitleLabel)
        centerBox.add(Box.createVerticalStrut(16))
        centerBox.add(formCard)
        centerBox.add(Box.createVerticalGlue())

        val scroll = JBScrollPane(centerBox).apply {
            border = null
        }

        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    private fun createSuccessView(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(20)

        val centerBox = JBPanel<JBPanel<*>>()
        centerBox.layout = BoxLayout(centerBox, BoxLayout.Y_AXIS)
        centerBox.alignmentX = JComponent.CENTER_ALIGNMENT

        val titleLabel = JBLabel("Connected successfully", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 18f)
            foreground = JBColor(Color(35, 130, 70), Color(45, 150, 80))
            alignmentX = JComponent.CENTER_ALIGNMENT
        }

        val subtitleLabel = JBLabel("Workspace validated and ready for task management.", SwingConstants.CENTER).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            alignmentX = JComponent.CENTER_ALIGNMENT
        }

        val card = JPanel()
        card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createEtchedBorder(),
            JBUI.Borders.empty(16)
        )
        card.maximumSize = Dimension(440, 180)

        val infoRow1 = JPanel(FlowLayout(FlowLayout.LEFT))
        infoRow1.add(JBLabel("Base App Token:").apply { font = font.deriveFont(Font.BOLD) })
        infoRow1.add(successBaseTokenLabel)

        val infoRow2 = JPanel(FlowLayout(FlowLayout.LEFT))
        infoRow2.add(JBLabel("Table ID:").apply { font = font.deriveFont(Font.BOLD) })
        infoRow2.add(successTableIdLabel)

        val infoRow3 = JPanel(FlowLayout(FlowLayout.LEFT))
        infoRow3.add(JBLabel("Status:").apply { font = font.deriveFont(Font.BOLD) })
        infoRow3.add(JBLabel("Active & Authenticated").apply { foreground = JBColor(Color(35, 130, 70), Color(45, 150, 80)) })

        val continueBtn = JButton("Continue to Dashboard", AllIcons.Actions.Forward).apply {
            alignmentX = JComponent.CENTER_ALIGNMENT
            addActionListener {
                checkAuthState()
            }
        }

        card.add(infoRow1)
        card.add(infoRow2)
        card.add(infoRow3)
        card.add(Box.createVerticalStrut(12))
        card.add(continueBtn)

        centerBox.add(Box.createVerticalGlue())
        centerBox.add(titleLabel)
        centerBox.add(Box.createVerticalStrut(8))
        centerBox.add(subtitleLabel)
        centerBox.add(Box.createVerticalStrut(20))
        centerBox.add(card)
        centerBox.add(Box.createVerticalGlue())

        panel.add(centerBox, BorderLayout.CENTER)
        return panel
    }

    private fun showStep2Feedback(message: String, isSuccess: Boolean) {
        val formattedMsg = "<html><body style='width: 380px'>" + message.replace("\n", "<br/>") + "</body></html>"
        step2FeedbackLabel.text = formattedMsg
        step2FeedbackLabel.foreground = if (isSuccess) JBColor(Color(35, 130, 70), Color(45, 150, 80)) else JBColor(Color(180, 50, 50), Color(200, 60, 60))
        step2FeedbackPanel.isVisible = true
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    private fun updateBaseSelectorDropdown() {
        val state = LarkAppSettingsState.getInstance()
        val items = mutableListOf<String>()
        state.savedBases.forEach { base ->
            items.add("📊 ${base.name} (${base.appToken.take(6)}...)")
        }
        items.add("➕ Connect Another Base Sheet...")

        val model = DefaultComboBoxModel(items.toTypedArray())
        baseSelectorCombo.model = model
        if (state.activeBaseIndex in 0 until state.savedBases.size) {
            baseSelectorCombo.selectedIndex = state.activeBaseIndex
        }
    }

    private fun setupDashboardUI() {
        val topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)
        topPanel.border = JBUI.Borders.empty(8)

        val filterRow1 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        filterRow1.add(JBLabel("Base:"))
        filterRow1.add(baseSelectorCombo)
        filterRow1.add(JBLabel("Table:"))
        filterRow1.add(tableSelectorCombo)
        filterRow1.add(searchField)
        filterRow1.add(refreshButton)

        val filterRow2 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        filterRow2.add(autoRefreshCombo)
        filterRow2.add(createButton)
        filterRow2.add(addBaseButton)
        filterRow2.add(disconnectButton)

        topPanel.add(filterRow1)
        topPanel.add(filterRow2)

        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.rowHeight = 28
        table.setShowGrid(true)
        table.intercellSpacing = Dimension(1, 1)

        val scrollPane = JBScrollPane(table)

        setupDetailPanel()

        val splitter = JBSplitter(true, 0.55f)
        splitter.firstComponent = scrollPane
        splitter.secondComponent = detailContainer

        val paginationPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        paginationPanel.add(paginationLabel)

        val toolbar = createToolbar()
        val northContainer = JPanel(BorderLayout())
        northContainer.add(toolbar.component, BorderLayout.NORTH)
        northContainer.add(topPanel, BorderLayout.SOUTH)

        dashboardPanel.add(northContainer, BorderLayout.NORTH)
        dashboardPanel.add(splitter, BorderLayout.CENTER)
        dashboardPanel.add(paginationPanel, BorderLayout.SOUTH)

        baseSelectorCombo.addActionListener {
            val selectedIndex = baseSelectorCombo.selectedIndex
            val state = LarkAppSettingsState.getInstance()
            if (selectedIndex == state.savedBases.size) {
                step1UrlInput.text = ""
                step2TokenInput.text = ""
                rootCardLayout.show(rootPanel, "AUTH_STEP_1")
            } else if (selectedIndex in state.savedBases.indices && selectedIndex != state.activeBaseIndex) {
                apiService.switchActiveBase(selectedIndex)
                loadDashboardData()
            }
        }

        tableSelectorCombo.addActionListener {
            val selectedIndex = tableSelectorCombo.selectedIndex
            if (selectedIndex in currentTables.indices) {
                val selectedTable = currentTables[selectedIndex]
                if (selectedTable.tableId != activeTableId) {
                    activeTableId = selectedTable.tableId
                    val state = LarkAppSettingsState.getInstance()
                    state.tableId = activeTableId
                    loadTableRecords(activeTableId)
                }
            }
        }

        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilters()
            override fun removeUpdate(e: DocumentEvent) = applyFilters()
            override fun changedUpdate(e: DocumentEvent) = applyFilters()
        })

        refreshButton.addActionListener { loadDashboardData() }

        autoRefreshCombo.addActionListener {
            val selectedIndex = autoRefreshCombo.selectedIndex
            autoRefreshTimer?.stop()
            val intervalMs = when (selectedIndex) {
                1 -> 60_000
                2 -> 300_000
                3 -> 600_000
                else -> 0
            }
            if (intervalMs > 0) {
                autoRefreshTimer = javax.swing.Timer(intervalMs) {
                    if (activeTableId.isNotBlank()) {
                        loadTableRecords(activeTableId)
                    }
                }
                autoRefreshTimer?.start()
            }
        }

        addBaseButton.addActionListener {
            step1UrlInput.text = ""
            step2TokenInput.text = ""
            rootCardLayout.show(rootPanel, "AUTH_STEP_1")
        }

        disconnectButton.addActionListener {
            if (Messages.showYesNoDialog(project, "Disconnect from all connected Lark Base workspaces?", "Disconnect Accounts", Messages.getQuestionIcon()) == Messages.YES) {
                autoRefreshTimer?.stop()
                apiService.disconnect()
                LarkAppSettingsNotifier.notifySettingsChanged()
                checkAuthState()
            }
        }

        createButton.addActionListener {
            val currentTableName = currentTables.find { it.tableId == activeTableId }?.name ?: "Active Table"

            val personFieldNames = currentFields.filter { it.type == 11 }.map { it.fieldName }
            val existingPersons = mutableSetOf<String>()
            for (rec in currentRecords) {
                for (fieldName in personFieldNames) {
                    val rawVal = rec.fields[fieldName]
                    val formattedName = formatFieldValue(rawVal)
                    if (formattedName.isNotBlank()) {
                        formattedName.split(",").forEach { name ->
                            val trimmed = name.trim()
                            if (trimmed.isNotEmpty()) existingPersons.add(trimmed)
                        }
                    }
                }
            }

            val dialog = DynamicCreateRecordDialog(project, currentTableName, currentFields, existingPersons.toList())
            if (dialog.showAndGet()) {
                val fieldsMap = dialog.getRecordFields()
                if (fieldsMap.isNotEmpty()) {
                    val state = LarkAppSettingsState.getInstance()
                    paginationLabel.text = "⏳ Creating record on Lark Base..."
                    createButton.isEnabled = false
                    ApplicationManager.getApplication().executeOnPooledThread {
                        val res = apiService.createRecord(state.appToken, activeTableId, state.userToken, fieldsMap)
                        SwingUtilities.invokeLater {
                            createButton.isEnabled = true
                            if (res.isSuccess) {
                                loadTableRecords(activeTableId)
                            } else {
                                val rawErr = res.exceptionOrNull()?.message ?: "Failed to create record"
                                paginationLabel.text = "❌ Record creation failed."
                                Messages.showErrorDialog(project, rawErr, "Lark Record Creation Failed")
                            }
                        }
                    }
                }
            }
        }

        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = table.selectedRow
                if (selectedRow >= 0 && selectedRow < filteredRecords.size) {
                    val rec = filteredRecords[selectedRow]
                    showRecordDetails(rec)
                } else {
                    detailCardLayout.show(detailContainer, "EMPTY")
                }
            }
        }
    }

    private fun setupDetailPanel() {
        detailContainer.add(emptyDetailPanel, "EMPTY")

        val contentBox = JPanel(BorderLayout(0, 6))
        contentBox.border = JBUI.Borders.empty(8)

        val headerPanel = JPanel(BorderLayout(12, 0))
        val titleBox = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2))
        titleBox.add(detailKeyLabel)
        titleBox.add(detailTitleLabel)

        val openWebBtn = JButton("Open in Lark Base", AllIcons.General.Web).apply {
            addActionListener {
                val url = LarkAppSettingsState.getInstance().larkUrl.ifEmpty { "https://www.larksuite.com" }
                BrowserUtil.browse(url)
            }
        }

        headerPanel.add(titleBox, BorderLayout.WEST)
        headerPanel.add(openWebBtn, BorderLayout.EAST)
        headerPanel.border = JBUI.Borders.customLineBottom(JBColor.border())

        val detailScroll = JBScrollPane(detailFieldsBox).apply {
            border = BorderFactory.createEtchedBorder()
        }

        contentBox.add(headerPanel, BorderLayout.NORTH)
        contentBox.add(detailScroll, BorderLayout.CENTER)

        activeDetailPanel.add(contentBox, BorderLayout.CENTER)
        detailContainer.add(activeDetailPanel, "ACTIVE")

        detailCardLayout.show(detailContainer, "EMPTY")
    }

    private fun loadDashboardData() {
        val state = LarkAppSettingsState.getInstance()
        val appToken = state.appToken
        val userToken = state.userToken

        if (appToken.isBlank() || userToken.isBlank()) return

        refreshButton.isEnabled = false
        paginationLabel.text = "⏳ Fetching tables from Lark Base..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = apiService.fetchTables(appToken, userToken)
            SwingUtilities.invokeLater {
                refreshButton.isEnabled = true
                if (result.isSuccess) {
                    currentTables = result.getOrNull() ?: emptyList()
                    updateTableSelectorDropdown()
                    if (currentTables.isNotEmpty()) {
                        val targetTable = currentTables.find { it.tableId == state.tableId } ?: currentTables.first()
                        activeTableId = targetTable.tableId
                        state.tableId = activeTableId
                        loadTableRecords(activeTableId)
                    } else {
                        paginationLabel.text = "No tables found in this Base."
                    }
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to fetch tables"
                    paginationLabel.text = "❌ $err"
                }
            }
        }
    }

    private fun updateTableSelectorDropdown() {
        val items = currentTables.map { "📋 ${it.name}" }.toTypedArray()
        tableSelectorCombo.model = DefaultComboBoxModel(if (items.isNotEmpty()) items else arrayOf("No Tables Found"))
        val selectedIndex = currentTables.indexOfFirst { it.tableId == activeTableId }
        if (selectedIndex >= 0) {
            tableSelectorCombo.selectedIndex = selectedIndex
        }
    }

    private fun loadTableRecords(tableId: String) {
        val state = LarkAppSettingsState.getInstance()
        val appToken = state.appToken
        val userToken = state.userToken

        if (appToken.isBlank() || tableId.isBlank() || userToken.isBlank()) return

        paginationLabel.text = "⏳ Fetching live records from Lark Base..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = apiService.fetchRecords(appToken, tableId, userToken)
            SwingUtilities.invokeLater {
                if (result.isSuccess) {
                    val pair = result.getOrNull()!!
                    currentFields = pair.first
                    currentRecords = pair.second
                    applyFilters()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Failed to fetch records"
                    paginationLabel.text = "❌ $err"
                }
            }
        }
    }

    private fun applyFilters() {
        val query = searchField.text.trim().lowercase()

        filteredRecords = currentRecords.filter { rec ->
            if (query.isEmpty()) true
            else rec.fields.values.any { valStr -> formatFieldValue(valStr).lowercase().contains(query) }
        }

        if (currentFields.isEmpty() && currentRecords.isNotEmpty()) {
            val recordKeys = currentRecords.flatMap { it.fields.keys }.distinct()
            if (recordKeys.isNotEmpty()) {
                currentFields = recordKeys.map { key ->
                    LarkFieldInfo(fieldId = key, fieldName = key, type = 1)
                }
            }
        }

        val headers = if (currentFields.isNotEmpty()) {
            currentFields.map { it.fieldName }.toTypedArray()
        } else {
            arrayOf("Record ID", "Data")
        }

        tableModel.setDataVector(emptyArray(), headers)

        filteredRecords.forEach { rec ->
            val rowData = if (currentFields.isNotEmpty()) {
                currentFields.map { field -> formatFieldValue(rec.fields[field.fieldName]) }.toTypedArray()
            } else {
                arrayOf(rec.recordId, rec.fields.toString())
            }
            tableModel.addRow(rowData)
        }

        paginationLabel.text = "Showing ${filteredRecords.size} live records from Lark Base"

        if (filteredRecords.isNotEmpty()) {
            table.setRowSelectionInterval(0, 0)
            showRecordDetails(filteredRecords[0])
        } else {
            detailCardLayout.show(detailContainer, "EMPTY")
        }
    }

    private fun formatFieldValue(value: Any?): String {
        if (value == null) return ""
        return when (value) {
            is Boolean -> if (value) "☑ Yes" else "☐ No"
            is String -> value
            is Number -> value.toString()
            is List<*> -> {
                value.mapNotNull { item ->
                    when (item) {
                        is Map<*, *> -> item["name"]?.toString() ?: item["text"]?.toString() ?: item["full_name"]?.toString() ?: item.toString()
                        else -> item?.toString()
                    }
                }.joinToString(", ")
            }
            is Map<*, *> -> {
                value["name"]?.toString() ?: value["text"]?.toString() ?: value["text_content"]?.toString() ?: value.toString()
            }
            else -> value.toString()
        }
    }

    private fun showRecordDetails(record: LarkRecord) {
        selectedRecord = record
        detailKeyLabel.text = "Record ID: ${record.recordId}"
        val firstVal = currentFields.firstOrNull()?.let { formatFieldValue(record.fields[it.fieldName]) }
        detailTitleLabel.text = if (!firstVal.isNull_or_empty()) firstVal else record.recordId

        detailFieldsBox.removeAll()
        for (field in currentFields) {
            val valStr = formatFieldValue(record.fields[field.fieldName])
            val rowPanel = JPanel(BorderLayout(8, 4)).apply {
                border = JBUI.Borders.empty(4, 0)
                val lbl = JBLabel("${field.fieldName}:").apply { font = font.deriveFont(Font.BOLD) }
                val valLbl = JBLabel(if (valStr.isNotEmpty()) valStr else "(empty)")
                add(lbl, BorderLayout.WEST)
                add(valLbl, BorderLayout.CENTER)
            }
            detailFieldsBox.add(rowPanel)
        }
        detailFieldsBox.revalidate()
        detailFieldsBox.repaint()

        detailCardLayout.show(detailContainer, "ACTIVE")
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.isEmpty()

    private fun createToolbar(): com.intellij.openapi.actionSystem.ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            addSeparator()
            add(OpenInBrowserAction())
            add(SettingsAction(project))
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "LarkConnectorToolbar",
            actionGroup,
            true
        )
        toolbar.targetComponent = dashboardPanel
        return toolbar
    }

    fun getContent(): JComponent = rootPanel

    private inner class RefreshAction :
        DumbAwareAction("Refresh", "Reload Lark tasks", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            loadDashboardData()
        }
    }

    private class OpenInBrowserAction :
        DumbAwareAction("Open in Browser", "Open Lark Base in external browser", AllIcons.General.Web) {
        override fun actionPerformed(e: AnActionEvent) {
            val url = LarkAppSettingsState.getInstance().larkUrl.ifEmpty { "https://www.larksuite.com" }
            BrowserUtil.browse(url)
        }
    }

    private class SettingsAction(private val project: Project) :
        DumbAwareAction("Settings", "Open Lark connector settings", AllIcons.General.Settings) {
        override fun actionPerformed(e: AnActionEvent) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, LarkAppSettingsConfigurable::class.java)
        }
    }
}
