package app.com.brd.plugin.lark

import app.com.brd.plugin.lark.model.LarkTaskItem
import app.com.brd.plugin.lark.service.LarkBitableApiService
import app.com.brd.plugin.lark.settings.LarkAppSettingsConfigurable
import app.com.brd.plugin.lark.settings.LarkAppSettingsNotifier
import app.com.brd.plugin.lark.settings.LarkAppSettingsState
import app.com.brd.plugin.lark.ui.BaseUrlHelpDialog
import app.com.brd.plugin.lark.ui.CreateTaskDialog
import app.com.brd.plugin.lark.ui.StatusBadgeCellRenderer
import app.com.brd.plugin.lark.ui.TokenHelpDialog
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
import com.intellij.ui.components.JBTextArea
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

    // Step 2 components
    private val step2TokenInput = JBPasswordField()
    private val step2FeedbackLabel = JBLabel().apply { font = font.deriveFont(Font.PLAIN, 12f) }
    private val step2FeedbackPanel = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
        border = JBUI.Borders.empty(6)
        isVisible = false
    }

    // Success Card components
    private val successBaseTokenLabel = JBLabel()
    private val successTableIdLabel = JBLabel()

    // Dashboard View components
    private val dashboardPanel = JPanel(BorderLayout())
    private var allTasks = listOf<LarkTaskItem>()
    private var filteredTasks = listOf<LarkTaskItem>()

    // Base Selector & Filters
    private val baseSelectorCombo = JComboBox<String>()
    private val typeCombo = JComboBox(arrayOf("All Types", "Bug", "Task", "Story", "Feature"))
    private val priorityCombo = JComboBox(arrayOf("All Priorities", "Critical", "High", "Medium", "Low"))
    private val statusCombo = JComboBox(arrayOf("All Statuses", "Problem", "In Progress", "Done", "To Do"))
    private val assigneeCombo = JComboBox(arrayOf("ANY", "Mubashir", "Haris Nazir", "Zoha Arif", "Muhammad A..."))
    private val searchField = SearchTextField(false)
    private val boardCombo = JComboBox(arrayOf("Main Table", "Monthly View", "Tasks/Assignee"))
    private val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)
    private val createButton = JButton("+ Add Record", AllIcons.General.Add)
    private val addBaseButton = JButton("+ Add Base Sheet", AllIcons.General.Add)
    private val disconnectButton = JButton("Disconnect", AllIcons.Actions.Exit)

    // Table
    private val columnNames = arrayOf("Key", "Type", "Summary", "Status", "Assignee", "Priority")
    private val tableModel = object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JBTable(tableModel)

    // Pagination
    private val paginationLabel = JBLabel("Showing 0 issues (Page 1)")
    private val prevPageBtn = JButton("<").apply { isEnabled = false }
    private val nextPageBtn = JButton(">").apply { isEnabled = false }

    // Detail Panel
    private val detailCardLayout = CardLayout()
    private val detailContainer = JPanel(detailCardLayout)

    private val emptyDetailPanel = JPanel(BorderLayout()).apply {
        val label = JBLabel("Select an issue to view details", SwingConstants.CENTER).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            font = font.deriveFont(Font.PLAIN, 13f)
        }
        add(label, BorderLayout.CENTER)
    }

    private val activeDetailPanel = JPanel(BorderLayout())
    private val detailTitleLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD, 15f) }
    private val detailKeyLabel = JBLabel().apply { font = font.deriveFont(Font.BOLD, 12f) }
    private val detailTypeLabel = JBLabel()
    private val detailStatusLabel = JBLabel()
    private val detailPriorityLabel = JBLabel()
    private val detailAssigneeLabel = JBLabel()
    private val detailDescriptionArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = JBUI.Borders.empty(8)
    }

    private var selectedTask: LarkTaskItem? = null

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
        val state = LarkAppSettingsState.getInstance()
        if (apiService.isConnected()) {
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

        // Pressing Enter triggers Continue
        step1UrlInput.addActionListener {
            continueBtn.doClick()
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
                rootCardLayout.show(rootPanel, "AUTH_STEP_2")
            }
        }

        val btnRow = JPanel(FlowLayout(FlowLayout.CENTER))
        btnRow.add(continueBtn)

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
        formCard.maximumSize = Dimension(520, 320)

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
        val guideText = JBLabel("<html><b>1.</b> Open Lark Developer Console (open.larksuite.com/app).<br/><b>2.</b> Select your App, go to <b>Permissions & Scopes</b>, and add <code>bitable:app</code>.<br/><b>3.</b> Copy your <b>User Access Token</b> from Test Notes / Credentials and paste it above.</html>").apply {
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

        step2FeedbackPanel.add(step2FeedbackLabel)

        // Pressing Enter inside password field triggers test connection
        step2TokenInput.addActionListener {
            testConnectBtn.doClick()
        }

        backBtn.addActionListener {
            rootCardLayout.show(rootPanel, "AUTH_STEP_1")
        }

        tokenHelpBtn.addActionListener {
            TokenHelpDialog(project).show()
        }

        testConnectBtn.addActionListener {
            val token = String(step2TokenInput.password).trim()
            val state = LarkAppSettingsState.getInstance()

            val testResult = apiService.testConnection(step1BaseUrl, token, state.appId, state.appSecret)

            if (testResult.isSuccess) {
                showStep2Feedback("✅ Connection successful!", true)
                successBaseTokenLabel.text = testResult.appToken
                successTableIdLabel.text = testResult.tableId
                rootCardLayout.show(rootPanel, "AUTH_SUCCESS")
            } else {
                showStep2Feedback("❌ ${testResult.message}", false)
            }
        }

        val primaryBtnRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        primaryBtnRow.add(testConnectBtn)

        val secondaryBtnRow = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        secondaryBtnRow.add(backBtn)
        secondaryBtnRow.add(tokenHelpBtn)

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
        step2FeedbackLabel.text = message
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

        // Filter Bar 1
        val filterRow1 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        filterRow1.add(JBLabel("Base:"))
        filterRow1.add(baseSelectorCombo)
        filterRow1.add(JBLabel("Type:"))
        filterRow1.add(typeCombo)
        filterRow1.add(JBLabel("Priority:"))
        filterRow1.add(priorityCombo)
        filterRow1.add(JBLabel("Status:"))
        filterRow1.add(statusCombo)
        filterRow1.add(JBLabel("Assignee:"))
        filterRow1.add(assigneeCombo)
        filterRow1.add(searchField)
        filterRow1.add(refreshButton)

        // Filter Bar 2
        val filterRow2 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        filterRow2.add(JBLabel("Table:"))
        filterRow2.add(boardCombo)
        filterRow2.add(createButton)
        filterRow2.add(addBaseButton)
        filterRow2.add(disconnectButton)

        topPanel.add(filterRow1)
        topPanel.add(filterRow2)

        // Configure Table
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.rowHeight = 28
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 0)

        table.columnModel.getColumn(3).cellRenderer = StatusBadgeCellRenderer()
        table.columnModel.getColumn(0).preferredWidth = 80
        table.columnModel.getColumn(1).preferredWidth = 60
        table.columnModel.getColumn(2).preferredWidth = 260
        table.columnModel.getColumn(3).preferredWidth = 110
        table.columnModel.getColumn(4).preferredWidth = 100
        table.columnModel.getColumn(5).preferredWidth = 70

        val scrollPane = JBScrollPane(table)

        setupDetailPanel()

        val splitter = JBSplitter(false, 0.65f)
        splitter.firstComponent = scrollPane
        splitter.secondComponent = detailContainer

        val paginationPanel = JPanel(FlowLayout(FlowLayout.CENTER, 8, 4))
        paginationPanel.add(prevPageBtn)
        paginationPanel.add(paginationLabel)
        paginationPanel.add(nextPageBtn)

        val toolbar = createToolbar()
        val northContainer = JPanel(BorderLayout())
        northContainer.add(toolbar.component, BorderLayout.NORTH)
        northContainer.add(topPanel, BorderLayout.SOUTH)

        dashboardPanel.add(northContainer, BorderLayout.NORTH)
        dashboardPanel.add(splitter, BorderLayout.CENTER)
        dashboardPanel.add(paginationPanel, BorderLayout.SOUTH)

        // Event Listeners
        baseSelectorCombo.addActionListener {
            val selectedIndex = baseSelectorCombo.selectedIndex
            val state = LarkAppSettingsState.getInstance()
            if (selectedIndex == state.savedBases.size) {
                // Clicked "+ Connect Another Base Sheet..."
                step1UrlInput.text = ""
                step2TokenInput.text = ""
                rootCardLayout.show(rootPanel, "AUTH_STEP_1")
            } else if (selectedIndex in state.savedBases.indices && selectedIndex != state.activeBaseIndex) {
                apiService.switchActiveBase(selectedIndex)
                loadDashboardData()
            }
        }

        val filterListener = java.awt.event.ActionListener { applyFilters() }
        typeCombo.addActionListener(filterListener)
        priorityCombo.addActionListener(filterListener)
        statusCombo.addActionListener(filterListener)
        assigneeCombo.addActionListener(filterListener)

        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilters()
            override fun removeUpdate(e: DocumentEvent) = applyFilters()
            override fun changedUpdate(e: DocumentEvent) = applyFilters()
        })

        refreshButton.addActionListener { loadDashboardData() }

        addBaseButton.addActionListener {
            step1UrlInput.text = ""
            step2TokenInput.text = ""
            rootCardLayout.show(rootPanel, "AUTH_STEP_1")
        }

        disconnectButton.addActionListener {
            if (Messages.showYesNoDialog(project, "Disconnect from all connected Lark Base workspaces?", "Disconnect Accounts", Messages.getQuestionIcon()) == Messages.YES) {
                apiService.disconnect()
                LarkAppSettingsNotifier.notifySettingsChanged()
                checkAuthState()
            }
        }

        createButton.addActionListener {
            val dialog = CreateTaskDialog(project)
            if (dialog.showAndGet()) {
                val newTask = apiService.addTask(
                    type = dialog.typeCombo.selectedItem?.toString() ?: "Bug",
                    summary = dialog.summaryField.text.trim().ifEmpty { "New Lark Task" },
                    status = dialog.statusCombo.selectedItem?.toString() ?: "To Do",
                    assignee = dialog.assigneeField.text.trim().ifEmpty { "Unassigned" },
                    priority = dialog.priorityCombo.selectedItem?.toString() ?: "Medium",
                    description = dialog.descriptionField.text.trim()
                )
                loadDashboardData()
                selectTaskInTable(newTask)
            }
        }

        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedRow = table.selectedRow
                if (selectedRow >= 0 && selectedRow < filteredTasks.size) {
                    val task = filteredTasks[selectedRow]
                    showTaskDetails(task)
                } else {
                    detailCardLayout.show(detailContainer, "EMPTY")
                }
            }
        }
    }

    private fun setupDetailPanel() {
        detailContainer.add(emptyDetailPanel, "EMPTY")

        val contentBox = JBPanel<JBPanel<*>>()
        contentBox.layout = BoxLayout(contentBox, BoxLayout.Y_AXIS)
        contentBox.border = JBUI.Borders.empty(12)

        val headerPanel = JPanel(BorderLayout())
        headerPanel.add(detailKeyLabel, BorderLayout.NORTH)
        headerPanel.add(detailTitleLabel, BorderLayout.CENTER)
        headerPanel.border = JBUI.Borders.customLineBottom(JBColor.border())

        val metaPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 6))
        metaPanel.add(JBLabel("Type:"))
        metaPanel.add(detailTypeLabel)
        metaPanel.add(JBLabel("Status:"))
        metaPanel.add(detailStatusLabel)
        metaPanel.add(JBLabel("Priority:"))
        metaPanel.add(detailPriorityLabel)
        metaPanel.add(JBLabel("Assignee:"))
        metaPanel.add(detailAssigneeLabel)

        val descLabel = JBLabel("Description:").apply { font = font.deriveFont(Font.BOLD) }
        val descScroll = JBScrollPane(detailDescriptionArea).apply {
            border = BorderFactory.createEtchedBorder()
        }

        val actionBox = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6))
        val markDoneBtn = JButton("Mark Done", AllIcons.Actions.Checked)
        val markProgressBtn = JButton("In Progress", AllIcons.Actions.Execute)
        val openWebBtn = JButton("Open in Lark Base", AllIcons.General.Web)

        markDoneBtn.addActionListener {
            selectedTask?.let {
                apiService.updateTaskStatus(it.id, "Done")
                loadDashboardData()
            }
        }

        markProgressBtn.addActionListener {
            selectedTask?.let {
                apiService.updateTaskStatus(it.id, "In Progress")
                loadDashboardData()
            }
        }

        openWebBtn.addActionListener {
            val url = LarkAppSettingsState.getInstance().larkUrl.ifEmpty { "https://www.larksuite.com" }
            BrowserUtil.browse(url)
        }

        actionBox.add(markDoneBtn)
        actionBox.add(markProgressBtn)
        actionBox.add(openWebBtn)

        contentBox.add(headerPanel)
        contentBox.add(Box.createVerticalStrut(8))
        contentBox.add(metaPanel)
        contentBox.add(Box.createVerticalStrut(12))
        contentBox.add(descLabel)
        contentBox.add(Box.createVerticalStrut(4))
        contentBox.add(descScroll)
        contentBox.add(Box.createVerticalStrut(12))
        contentBox.add(actionBox)

        activeDetailPanel.add(contentBox, BorderLayout.CENTER)
        detailContainer.add(activeDetailPanel, "ACTIVE")

        detailCardLayout.show(detailContainer, "EMPTY")
    }

    private fun loadDashboardData() {
        allTasks = apiService.fetchTasks()
        applyFilters()
    }

    private fun applyFilters() {
        val selectedType = typeCombo.selectedItem?.toString() ?: "All Types"
        val selectedPriority = priorityCombo.selectedItem?.toString() ?: "All Priorities"
        val selectedStatus = statusCombo.selectedItem?.toString() ?: "All Statuses"
        val selectedAssignee = assigneeCombo.selectedItem?.toString() ?: "ANY"
        val query = searchField.text.trim().lowercase()

        filteredTasks = allTasks.filter { task ->
            val matchType = selectedType == "All Types" || task.type.equals(selectedType, ignoreCase = true)
            val matchPriority = selectedPriority == "All Priorities" || task.priority.equals(selectedPriority, ignoreCase = true)
            val matchStatus = selectedStatus == "All Statuses" || task.status.equals(selectedStatus, ignoreCase = true)
            val matchAssignee = selectedAssignee == "ANY" || task.assignee.contains(selectedAssignee, ignoreCase = true)
            val matchQuery = query.isEmpty() || task.key.lowercase().contains(query) || task.summary.lowercase().contains(query)

            matchType && matchPriority && matchStatus && matchAssignee && matchQuery
        }

        tableModel.rowCount = 0
        filteredTasks.forEach { task ->
            tableModel.addRow(arrayOf(
                task.key,
                task.type,
                task.summary,
                task.status,
                task.assignee,
                task.priority
            ))
        }

        paginationLabel.text = "Showing ${filteredTasks.size} issues (Page 1)"

        if (filteredTasks.isNotEmpty()) {
            table.setRowSelectionInterval(0, 0)
            showTaskDetails(filteredTasks[0])
        } else {
            detailCardLayout.show(detailContainer, "EMPTY")
        }
    }

    private fun showTaskDetails(task: LarkTaskItem) {
        selectedTask = task
        detailKeyLabel.text = task.key
        detailTitleLabel.text = task.summary
        detailTypeLabel.text = task.type
        detailStatusLabel.text = task.status
        detailPriorityLabel.text = task.priority
        detailAssigneeLabel.text = task.assignee
        detailDescriptionArea.text = task.description.ifEmpty { "No description provided." }

        detailCardLayout.show(detailContainer, "ACTIVE")
    }

    private fun selectTaskInTable(task: LarkTaskItem) {
        val index = filteredTasks.indexOfFirst { it.id == task.id }
        if (index != -1) {
            table.setRowSelectionInterval(index, index)
            showTaskDetails(task)
        }
    }

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
