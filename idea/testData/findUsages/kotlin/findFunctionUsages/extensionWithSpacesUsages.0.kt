// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
class GitRepository

fun GitRepository.`me<caret>thod name`() { // highlighted as 'not used', Find Usages does nothing.
}

fun f(repo: GitRepository) {
    repo.`method name`()  // Go to declaration works fine
}