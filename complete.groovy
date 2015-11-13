import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import java.math.RoundingMode
import java.text.DecimalFormat

enableCache = {-> false}

def completed = ''
if (isEpic(issue)) {

    def numberOfIssuesOfEpic
    def numberOfCompletedIssues
    def issuesOfEpic = getEpicLinkManager().getIssuesInEpic(issue)
    numberOfIssuesOfEpic = issuesOfEpic.size()
    numberOfCompletedIssues = 0
    for (issueOfEpic in issuesOfEpic) {

        def Issue issueObj = (Issue)issueOfEpic
        def statusCategoryKey = getStatusCategoryKey(issueObj)
        if ("done".equals(statusCategoryKey)) {
            numberOfCompletedIssues++
        }
    }
    completed = (numberOfCompletedIssues * 100) / numberOfIssuesOfEpic
    completed = "Completed " + formatPercent(completed) + "%"

}

return completed




def getStatusCategoryKey(Issue issue) {
    issue.getStatusObject().getStatusCategory().getKey();
}

def formatPercent(doubleValue) {

    def  DecimalFormat decimalFormat = new DecimalFormat("0");
    decimalFormat.setRoundingMode(RoundingMode.HALF_UP);

    return decimalFormat.format(doubleValue);
}

def isEpic(issue) {

    def epicKey = "Epic"
    epicKey.equals(issue.issueType.name)
}

def getEpicLinkManager() {

    def ghPlugin = ComponentAccessor.getPluginAccessor().getEnabledPlugin("com.pyxis.greenhopper.jira")
    def descriptor = ghPlugin.getModuleDescriptor("greenhopper-launcher")
    def applicationContext = descriptor.getModule().greenHopperCacheManager.applicationContext
    def beanInstance = applicationContext.getBean("epicLinkManagerImpl")

    return beanInstance;
}