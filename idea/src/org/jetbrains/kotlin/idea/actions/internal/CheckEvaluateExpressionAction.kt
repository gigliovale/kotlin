/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.ui.breakpoints.LineBreakpoint
import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RunProfileState
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.TimeoutUtil
import com.sun.jdi.ThreadReference
import org.jetbrains.kotlin.idea.debugger.KotlinEditorTextProvider
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinCodeFragmentFactory
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluationBuilder
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.JetExpression
import org.jetbrains.kotlin.psi.JetFile
import org.jetbrains.kotlin.psi.JetNamedFunction
import org.jetbrains.kotlin.utils.fileUtils.readTextOrEmpty
import java.io.File
import java.util.ArrayList
import java.util.HashSet
import kotlin.properties.Delegates

public class CheckEvaluateExpressionAction : AnAction() {
    private val logger = Logger.getInstance(javaClass)

    private var outputFile: File by Delegates.notNull()
    private var knownExceptions: String by Delegates.notNull()

    private val REPORTED_EXCEPTIONS = hashSetOf(
            "Please specify constructor invocation"   // REPORTED
    )

    override fun actionPerformed(e: AnActionEvent) {
        val project = CommonDataKeys.PROJECT.getData(e.getDataContext())!!

        val basePath = project.getBasePath()
        if (basePath == null) return

        outputFile = File(basePath + "/evaluateExpressionLog.txt")
        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }
        else {
            outputFile.writeText("")
        }

        val knownExceptionsFile = File(basePath + "/knownExceptions.txt")
        if (knownExceptionsFile.exists()) {
            knownExceptions = knownExceptionsFile.readTextOrEmpty()
        }
        else {
            knownExceptions = ""
        }

        val sourceFiles = selectedKotlinFiles(e).toList().filter { it.getPackageFqName().asString().contains("completion") }

        myPauseScriptListener = null

        val debuggerContext = DebuggerManagerEx.getInstanceEx(project).getContext()

        val breakpoints = createBreakpoints(project, sourceFiles)

        onBreakpoint(project, object : SuspendContextRunnable {
            override fun run(suspendContext: SuspendContextImpl) {
                checkEvaluateExpression(project, suspendContext)
            }
        })

        val listener = object : DebugProcessAdapterImpl() {
            override fun processDetached(process: DebugProcessImpl?, closedByUser: Boolean) {
                super.processDetached(process, closedByUser)

                myPauseScriptListener?.processDetached(process, closedByUser)
                process?.removeDebugProcessListener(this)

                ApplicationManager.getApplication().invokeAndWait(Runnable {
                    val breakpointManager = DebuggerManagerEx.getInstanceEx(project)?.getBreakpointManager()!!
                    breakpoints.forEach {
                        breakpointManager.removeBreakpoint(it)
                    }
                }, ModalityState.defaultModalityState())
            }
        }

        debuggerContext.getDebugProcess()?.addDebugProcessListener(listener)

        DebuggerInvocationUtil.invokeLater(project, Runnable { debuggerContext?.getDebuggerSession()?.resume() }, ModalityState.defaultModalityState())
    }

    private fun createBreakpoints(project: Project, sourceFiles: List<JetFile>): ArrayList<LineBreakpoint> {
        val breakpointsList = ArrayList<LineBreakpoint>()
        val addBreakpoints = Runnable() {
            for (file in sourceFiles) {

                val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
                val breakpointManager = DebuggerManagerEx.getInstanceEx(project)?.getBreakpointManager()!!

                for (line in 0..document.getLineCount() - 1) {
                    val elementAt = file.findElementAt(document.getLineStartOffset(line))
                    val parent = PsiTreeUtil.getParentOfType(elementAt, javaClass<JetNamedFunction>())?.getBodyExpression()
                    if (parent != null && document.getLineNumber(parent.getTextRange().getStartOffset()) < line) {
                        val breakpoint: LineBreakpoint? = breakpointManager.addLineBreakpoint(document, line)
                        if (breakpoint != null) {
                            breakpointsList.add(breakpoint)

                            println("LineBreakpoint created at " + file.getName() + ":" + line);
                        }
                    }
                }

            }

        }

        DebuggerInvocationUtil.invokeLater(project, addBreakpoints, ModalityState.defaultModalityState())

        return breakpointsList
    }


    private fun checkEvaluateExpression(project: Project, suspendContext: SuspendContextImpl) {
        val context = EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy(), suspendContext.getFrameProxy().getStackFrame().thisObject())
        val sourcePosition = ContextUtil.getSourcePosition(context)
        if (sourcePosition != null && sourcePosition.getLine() != -1) {
            runReadAction {
                println("++++++++++++++++++++++++++++++")
                println("NEXT FILE ${sourcePosition.getFile().getName()}:${sourcePosition.getLine()}")
                val elementAt = sourcePosition.getElementAt()
                val functionBody = PsiTreeUtil.getParentOfType(elementAt, javaClass<JetNamedFunction>()).getBodyExpression()
                if (functionBody == null) return@runReadAction

                val expressions = findExpressions(functionBody, sourcePosition.getLine())

                for (expressionText in expressions) {
                    val text = TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expressionText)
                    val codeFragment = KotlinCodeFragmentFactory().createCodeFragment(text, elementAt, project)

                    try {
                        val evaluator = KotlinEvaluationBuilder.build(codeFragment, sourcePosition)

                        val start = System.currentTimeMillis()
                        val value = evaluator.evaluate(context)
                        val durationSeconds = (System.currentTimeMillis() - start) / 1000.0
                        val duration = durationSeconds.toString()
                        val importantPostfix = if (durationSeconds > 5) " - TOO LONG EVALUATION" else ""
                        println("Expression '${expressionText}' was evaluated to '${value.toString()}' in ${duration.substring(0, Math.min(5, duration.length()))}s$importantPostfix")
                    }
                    catch(e: Exception) {
                        val message = e.getMessage()

                        val exceptionText: String

                        if (message == null || REPORTED_EXCEPTIONS.firstOrNull { message.startsWith(it) } != null) {
                            continue
                        }
                        else {
                            println("-------------------------------------------")
                            if (message.contains("Unresolved reference:")) {
                                val offset = elementAt.getTextRange().getStartOffset() - functionBody.getTextRange().getStartOffset()
                                val functionText = functionBody.getText()

                                val functionTextWithCaret = functionText.substring(0, offset) +
                                                            "<CARET>" +
                                                            functionText.substring(offset, Math.min(functionText.length(), offset + 40)) +
                                                            "..."

                                exceptionText = "Maybe in invisible in current scope:\n" + message + "\n$functionTextWithCaret"
                            }
                            else {
                                exceptionText = "Couldn't evaluate expression:\n${codeFragment.getText()}\nposition: ${sourcePosition.getFile()}:${sourcePosition.getLine()}\n${ExceptionUtil.getThrowableText(e)}"
                            }

                            if (knownExceptions.contains(expressionText)) continue

                            logger.error(exceptionText)
                            println(exceptionText)
                            println("-------------------------------------------")
                            REPORTED_EXCEPTIONS.add(message)
                        }
                    }
                }

                onBreakpoint(project, object : SuspendContextRunnable {
                    override fun run(suspendContext: SuspendContextImpl) {
                        checkEvaluateExpression(project, suspendContext)
                    }
                })

                resume(suspendContext)
            }
        }

    }

    private val myScriptRunnables = ArrayList<SuspendContextRunnable>()
    private var myPauseScriptListener: DebugProcessListener? = null

    protected fun onBreakpoint(project: Project, runnable: SuspendContextRunnable) {
        if (myPauseScriptListener == null) {
            val debugProcess = DebuggerManagerEx.getInstanceEx(project).getContext().getDebugProcess()!!

            myPauseScriptListener = DelayedEventsProcessListener(object : DebugProcessAdapterImpl() {
                override fun paused(suspendContext: SuspendContextImpl) {
                    try {
                        if (myScriptRunnables.isEmpty()) {
                            println("resuming ")
                            resume(suspendContext)
                            return
                        }
                        val suspendContextRunnable = myScriptRunnables.remove(0)
                        suspendContextRunnable.run(suspendContext)
                    }
                    catch (e: Exception) {
                    }
                    catch (e: AssertionError) {
                    }
                }

                override fun resumed(suspendContext: SuspendContextImpl?) {
                    val pausedContext = debugProcess.getSuspendManager().getPausedContext()
                    if (pausedContext != null) {
                        debugProcess.getManagerThread().schedule(object : SuspendContextCommandImpl(pausedContext) {
                            throws(javaClass<Exception>())
                            override fun contextAction() {
                                paused(pausedContext)
                            }
                        })
                    }
                }

                override fun processDetached(process: DebugProcessImpl?, closedByUser: Boolean) {
                    myScriptRunnables.clear()
                    super.processDetached(process, closedByUser)
                }
            })
            debugProcess.addDebugProcessListener(myPauseScriptListener)
        }
        myScriptRunnables.add(runnable)
    }

    protected fun resume(context: SuspendContextImpl) {
        val debugProcess = context.getDebugProcess()
        debugProcess.getManagerThread().schedule(debugProcess.createResumeCommand(context))
    }

    private fun findExpressions(function: JetExpression, currentLine: Int): Set<String> {
        val file = function.getContainingJetFile()
        val document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile())
        if (document == null) return emptySet()

        val startLineNumber = document.getLineNumber(function.getTextRange().getStartOffset()) + 1
        val endLineNumber = Math.min(document.getLineNumber(function.getTextRange().getEndOffset()) - 1, currentLine - 1)

        val result: MutableSet<String> = HashSet()

        val start = document.getLineStartOffset(startLineNumber)
        val end = document.getLineEndOffset(endLineNumber)
        for (offset in (start..end).step(3)) {
            val elementAt = file.findElementAt(offset)
            if (elementAt != null) {
                val expression = KotlinEditorTextProvider.findExpressionInner(elementAt, true)
                if (expression != null) {
                    result.add(KotlinEditorTextProvider.getElementInfo(expression) { it.getText() })
                }
            }
        }

        return result
    }

    override fun update(e: AnActionEvent) {
        if (!KotlinInternalMode.enabled) {
            e.getPresentation().setVisible(false)
            e.getPresentation().setEnabled(false)
        }
    }

    private inner class DelayedEventsProcessListener(private val myTarget: DebugProcessAdapterImpl) : DebugProcessListener {

        override fun threadStarted(proc: DebugProcess, thread: ThreadReference) {
        }

        override fun threadStopped(proc: DebugProcess, thread: ThreadReference) {
        }

        override fun paused(suspendContext: SuspendContext) {
            pauseExecution()
            myTarget.paused(suspendContext)
        }

        override fun resumed(suspendContext: SuspendContext) {
            pauseExecution()
            myTarget.resumed(suspendContext)
            myScriptRunnables.add(object : SuspendContextRunnable {
                override fun run(suspendContext: SuspendContextImpl) {
                    checkEvaluateExpression(suspendContext.getDebugProcess().getProject(), suspendContext)
                }
            })
        }

        override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
            myTarget.processDetached(process, closedByUser)
        }

        override fun processAttached(process: DebugProcess) {
            myTarget.processAttached(process)
        }

        override fun connectorIsReady() {
            myTarget.connectorIsReady()
        }

        override fun attachException(state: RunProfileState, exception: ExecutionException, remoteConnection: RemoteConnection) {
            myTarget.attachException(state, exception, remoteConnection)
        }

        private fun pauseExecution() {
            TimeoutUtil.sleep(10)
        }
    }

    private fun println(s: String) {
        outputFile.appendText(s)
        outputFile.appendText("\n")
    }

    private fun selectedKotlinFiles(e: AnActionEvent): Sequence<JetFile> {
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return sequenceOf()
        val project = CommonDataKeys.PROJECT.getData(e.getDataContext()) ?: return sequenceOf()
        return allKotlinFiles(virtualFiles, project)
    }

    private fun allKotlinFiles(filesOrDirs: Array<VirtualFile>, project: Project): Sequence<JetFile> {
        val manager = PsiManager.getInstance(project)
        return allFiles(filesOrDirs)
                .sequence()
                .map { manager.findFile(it) as? JetFile }
                .filterNotNull()
    }

    private fun allFiles(filesOrDirs: Array<VirtualFile>): Collection<VirtualFile> {
        val result = ArrayList<VirtualFile>()
        for (file in filesOrDirs) {
            VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Unit>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    result.add(file)
                    return true
                }
            })
        }
        return result
    }
}
