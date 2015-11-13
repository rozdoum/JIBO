import com.atlassian.jira.config.StatusCategoryManager
import com.atlassian.jira.config.StatusManager
import com.atlassian.jira.issue.status.Status
import com.atlassian.jira.issue.status.category.StatusCategory
import com.atlassian.jira.workflow.IssueWorkflowManager
import com.opensymphony.workflow.loader.ActionDescriptor
//import org.apache.log4j.Category
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.apache.log4j.LogManager


@Field def log = LogManager.getLogger("com.onstatuschange.jira.groovy.PostFunction")
@Field def statusCategoryManager = ComponentAccessor.getComponent(StatusCategoryManager.class)
@Field def statusManager = ComponentAccessor.getComponent(StatusManager.class)
@Field def descriptor = transientVars.get("descriptor")
@Field def epicLinkManager = getEpicLinkManager();
@Field def Set statusSet = new HashSet()

def Issue epic = getEpic((Issue)issue)
if (epic != null) {

    println("epic: " + epic)
    println("transientVars: " + transientVars)
    println("issueType: " + epic.issueType.name)
    println("status name: " + ((Issue)issue).getStatusObject().getName())
    println("status id: " + ((Issue)issue).getStatusObject().getId())

    def issueKey = issue.getKey()
    def issuesOfEpic = epicLinkManager.getIssuesInEpic(epic)
    for (issueOfEpic in issuesOfEpic) {

        def Issue issueObj = (Issue)issueOfEpic
        def statusCategoryKey
        if (issueObj.getKey().equals(issueKey)) {
            def statusName = getNextStepName()
            statusCategoryKey = getStatusCategoryKey(statusName)
        } else {
            statusCategoryKey = getStatusCategoryKey(issueObj)
        }

        statusSet.add(statusCategoryKey)
    }

    def String statusCategoryToSet;
    if (hasOnlyComplete()) {
        statusCategoryToSet = StatusCategory.COMPLETE
    } else if (hasOnlyToDo()) {
        statusCategoryToSet = StatusCategory.TO_DO
    } else if (hasOnlyInProgress() || hasInProgress() || hasComplete()) {
        statusCategoryToSet = StatusCategory.IN_PROGRESS
    }

    println("statusSet: " + statusSet)
    println("statusCategoryToSet: " + statusCategoryToSet)

    setStatus(epic, statusCategoryToSet)
}
return true




def getEpicLinkManager() {

    def ghPlugin = ComponentAccessor.getPluginAccessor().getEnabledPlugin("com.pyxis.greenhopper.jira")
    def descriptor = ghPlugin.getModuleDescriptor("greenhopper-launcher")
    def applicationContext = descriptor.getModule().greenHopperCacheManager.applicationContext
    def beanInstance = applicationContext.getBean("epicLinkManagerImpl")

    return beanInstance;
}

def getEpic(Issue issue) {

    def Issue epic = null
    if ( isNotEpic(issue) ) {

        def epicOption = epicLinkManager.getEpic(issue)
        if (epicOption.isDefined()) {

            epic = epicOption.get()
        } else {

            log.info("Epic was not found.");
            println("Epic was not found.")
        }
    }

    return epic
}

def isNotEpic(Issue issue) {

    def epicKey = "Epic"
    !epicKey.equals(issue.issueType.name)
}

def getStatusCategoryKey(Issue issue) {
    issue.getStatusObject().getStatusCategory().getKey();
}

def getStatusCategoryKey(String statusName) {

    def Status resultStatus = null;
    def Collection<Status> statuses = statusManager.getStatuses();
    for (Status status : statuses) {

        if (status.getName().equals(statusName)) {

            resultStatus = status;
            break;
        }
    }

    def category = resultStatus.getStatusCategory()
    category.getKey();
}

def getNextStepName() {

    def actionId = transientVars.get("actionId")
    def action = descriptor.getAction(actionId)
    getStepName(action)
}

def getStepName(action) {

    def stepId = action.getUnconditionalResult()?.step
    def step = descriptor.getStep(stepId)
    step.getName()
}

def setStatus(Issue issue, String statusCategoryToSet) {

    def currentStatus = getStatusCategoryKey(issue)
    if (statusCategoryToSet.equals(currentStatus)) {

        log.info("Epic has correct status - " + currentStatus + ". It will not be changed.")
        println("Epic has correct status - " + currentStatus + ". It will not be changed.")
    } else {

        def IssueWorkflowManager issueWorkflowManager = ComponentAccessor.getComponentOfType(IssueWorkflowManager.class);
        def Collection<ActionDescriptor> actions = issueWorkflowManager.getAvailableActions(issue);
        def Integer actionId
        for (action in actions) {

            def stepName = getStepName(action)
            def key = getStatusCategoryKey(stepName)
            if (statusCategoryToSet.equals(key)) {
                actionId = action.getId()
                break
            }
        }

        if (actionId == null) {
            log.error("Corresponding action wasn't found for statusCategory - " + statusCategoryToSet)
            println("---------- ERROR -------------------------")
            println("Corresponding action wasn't found for statusCategory - " + statusCategoryToSet)
            return
        }

        setStatus(issue, actionId)
    }
}

def setStatus(Issue issue, int actionId) {

    def issueService = ComponentAccessor.getIssueService()
    def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()

    def issueInputParameters = issueService.newIssueInputParameters()
//    issueInputParameters.with {
//        setResolutionId("1") // resolution of "Fixed"
//        setComment("*Resolving* as a result of the *Resolve* action being applied to the parent.")
//    }

    def validationResult = issueService.validateTransition(user, issue.id, actionId, issueInputParameters)
    if (validationResult.isValid()) {

        def issueResult = issueService.transition(user, validationResult)
        if (!issueResult.isValid()) {

            log.warn("Failed to transition task ${issue.key}, errors: ${issueResult.errorCollection}")
        }
    } else {

        log.warn("Could not transition task ${issue.key}, errors: ${validationResult.errorCollection}")
    }
}

def hasOnlyInProgress() {
    statusSet.size() == 1 && hasInProgress()
}

def hasInProgress() {
    statusSet.contains(StatusCategory.IN_PROGRESS)
}

def hasOnlyToDo() {
    statusSet.size() == 1 && hasToDo()
}

def hasToDo() {
    statusSet.contains(StatusCategory.TO_DO)
}

def hasOnlyComplete() {
    statusSet.size() == 1 && hasComplete()
}

def hasComplete() {
    statusSet.contains(StatusCategory.COMPLETE)
}

def hasOnlyOne() {
    statusSet.size() == 1
}