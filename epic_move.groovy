import com.atlassian.jira.config.StatusCategoryManager
import com.atlassian.jira.config.StatusManager
import com.atlassian.jira.issue.status.Status
import com.atlassian.jira.workflow.IssueWorkflowManager
import com.opensymphony.workflow.loader.ActionDescriptor
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.apache.log4j.LogManager


@Field def String UNDEFINED = "undefined";
@Field def String TO_DO = "new";
@Field def String IN_PROGRESS = "indeterminate";
@Field def String COMPLETE = "done";

@Field def log = LogManager.getLogger("com.onstatuschange.jira.groovy.PostFunction")
@Field def statusCategoryManager = ComponentAccessor.getComponent(StatusCategoryManager.class)
@Field def statusManager = ComponentAccessor.getComponent(StatusManager.class)
@Field def descriptor = transientVars.get("descriptor")
@Field def epicLinkManager = getEpicLinkManager();
@Field def Set statusSet = new HashSet()
@Field def Map<String, List> prioritySteps = getPrioritySteps()


def Issue epic = getEpic((Issue)issue)
if (epic != null) {

    println("status name: " + ((Issue)issue).getStatusObject().getName())

    def issueKey = issue.getKey()
    def issuesOfEpic = epicLinkManager.getIssuesInEpic(epic)
    for (issueOfEpic in issuesOfEpic) {

        def Issue issueObj = (Issue)issueOfEpic
        def statusCategoryKey
        if (issueObj.getKey().equals(issueKey)) {
            def statusName = getTargetStepName()
            statusCategoryKey = getStatusCategoryKey(statusName)
        } else {
            statusCategoryKey = getStatusCategoryKey(issueObj)
        }

        statusSet.add(statusCategoryKey)
    }

    def String statusCategoryToSet;
    if (hasOnlyComplete()) {
        statusCategoryToSet = COMPLETE
    } else if (hasOnlyToDo()) {
        statusCategoryToSet = TO_DO
    } else if (hasOnlyInProgress() || hasInProgress() || hasComplete()) {
        statusCategoryToSet = IN_PROGRESS
    }

    println("statusSet: " + statusSet)
    println("statusCategoryToSet: " + statusCategoryToSet)

    def currentStatus = getStatusCategoryKey(epic)
    if (statusCategoryToSet.equals(currentStatus)) {

        println("Epic has correct status - " + currentStatus + ". It will not be changed.")
    } else {

        def actionIdToSet = gerActionIdToSet(epic, statusCategoryToSet)
        if (actionIdToSet == null) {

            actionIdToSet = gerActionIdToSet(epic, TO_DO)
            setStatus(epic, actionIdToSet)

            actionIdToSet = gerActionIdToSet(epic, statusCategoryToSet)

            if (actionIdToSet == null) {

                println("---------- ERROR -------------------------")
                println("Corresponding action wasn't found for statusCategory - " + statusCategoryToSet)
                return
            }
        }

        setStatus(epic, actionIdToSet)
    }
}
return true


////////////////////////////////////////////////////////////////////////////////////////////////


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

def getTargetStepName() {

    def actionId = transientVars.get("actionId")
    def action = descriptor.getAction(actionId)
    getStepName(action)
}

def getStepName(action) {

    def stepId = action.getUnconditionalResult()?.step
    def step = descriptor.getStep(stepId)
    step.getName()
}

def gerActionIdToSet(Issue issue, String statusCategoryToSet) {

    def IssueWorkflowManager issueWorkflowManager = ComponentAccessor.getComponentOfType(IssueWorkflowManager.class);
    def Collection<ActionDescriptor> actions = issueWorkflowManager.getAvailableActions(issue);

    printSctions(actions) //TODO: ONLY FOR DEBUG

    def stepNameList = prioritySteps.get(statusCategoryToSet)
    def Integer actionId = gerActionIdByStepName(stepNameList, actions)

    if(actionId == null) {
        actionId = gerActionIdByCategory(actions, statusCategoryToSet)
    }

    return actionId
}

def gerActionIdByCategory(actions, String statusCategoryToSet) {

    def Integer actionId
    for (action in actions) {

        def stepName = getStepName(action)
        def statusCategory = getStatusCategoryKey(stepName)
        if (statusCategoryToSet.equals(statusCategory)) {

            actionId = action.getId()
            break
        }
    }

    return actionId
}

def gerActionIdByStepName(stepNameList, actions) {

    def Integer actionId
    for (action in actions) {

        def stepName = getStepName(action)
        if (stepNameList.contains(stepName)) {

            actionId = action.getId()
            break
        }
    }

    return actionId
}

def printSctions(actions) {
    println("--------------------------------")
    for (action in actions) {

        def stepName = getStepName(action)
        def key = getStatusCategoryKey(stepName)
        println(stepName + "  (" + key + ")")
    }
    println("--------------------------------")
}

def setStatus(Issue issue, int actionId) {

    def issueService = ComponentAccessor.getIssueService()
    def user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()

    def issueInputParameters = issueService.newIssueInputParameters()
    /*issueInputParameters.with {
        setResolutionId("1") // resolution of "Fixed"
        setComment("*Resolving* as a result of the *Resolve* action being applied to the parent.")
    }*/

    def issueResult
    def validationResult = issueService.validateTransition(user, issue.id, actionId, issueInputParameters)
    if (validationResult.isValid()) {

        issueResult = issueService.transition(user, validationResult)
        if (!issueResult.isValid()) {

            log.warn("Failed to transition task ${issue.key}, errors: ${issueResult.errorCollection}")
        }
    } else {

        log.warn("Could not transition task ${issue.key}, errors: ${validationResult.errorCollection}")
    }

    return issueResult == null ? false : issueResult.isValid()
}

def getPrioritySteps() {

    def prioritySteps = new HashMap()
    prioritySteps.put(TO_DO, Arrays.asList("Open", "To Do"))
    prioritySteps.put(IN_PROGRESS, Arrays.asList("In Progress"))
    prioritySteps.put(COMPLETE, Arrays.asList("Resolved", "Done"))

    return prioritySteps
}

def hasOnlyInProgress() {
    statusSet.size() == 1 && hasInProgress()
}

def hasInProgress() {
    statusSet.contains(IN_PROGRESS)
}

def hasOnlyToDo() {
    statusSet.size() == 1 && hasToDo()
}

def hasToDo() {
    statusSet.contains(TO_DO)
}

def hasOnlyComplete() {
    statusSet.size() == 1 && hasComplete()
}

def hasComplete() {
    statusSet.contains(COMPLETE)
}

def hasOnlyOne() {
    statusSet.size() == 1
}