package dk.silverbullet.kih.auditlog

import dk.silverbullet.kih.api.auditlog.AuditLogPermissionName
import dk.silverbullet.kih.api.auditlog.SkipAuditLog
import grails.plugin.springsecurity.annotation.Secured

import java.text.DateFormat
import java.text.SimpleDateFormat

@SkipAuditLog
@Secured([AuditLogPermissionName.AUDITLOG_NONE])
class AuditLogEntryController {

    private static final String GRAILS_ANONYMOUS_USER = "__grails.anonymous.user__"
    static allowedMethods = [save: "POST", update: "POST", delete: "POST"]

    @Secured([AuditLogPermissionName.AUDITLOG_READ])
    def index() {
        redirect(action: "list", params: params)
    }

    @Secured([AuditLogPermissionName.AUDITLOG_READ])
    def list(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        params.order = "desc"
        params.sort = "id"

        def controllers = AuditLogControllerEntity.executeQuery(
                "SELECT DISTINCT a.controllerName FROM AuditLogControllerEntity as a")
        def actions = AuditLogControllerEntity.executeQuery(
                "SELECT DISTINCT a.actionName FROM AuditLogControllerEntity as a")

        def auditLogEntryInstanceList = AuditLogEntry.list(params)
        auditLogEntryInstanceList.each { entry -> checkForAnonymousUser(entry) }

        [auditLogEntryInstanceList: auditLogEntryInstanceList,
         auditLogEntryInstanceTotal: AuditLogEntry.count(),
         controllers:controllers,
         actions: actions]
    }

    @Secured([AuditLogPermissionName.AUDITLOG_READ])
    def show(Long id) {
        def auditLogEntryInstance = AuditLogEntry.get(id)
        if (!auditLogEntryInstance) {
            flash.message = message(code: 'default.not.found.message',
                    args: [message(code: 'auditLogEntry.label',
                            default: 'AuditLogEntry'), id])
            redirect(action: "list")
            return
        }
        checkForAnonymousUser(auditLogEntryInstance)
        [auditLogEntryInstance: auditLogEntryInstance]
    }

    @Secured([AuditLogPermissionName.AUDITLOG_READ])
    def ajaxGetActions() {
        log.debug "Params: " + params
        def chosenController = params.c

        def actions = AuditLogControllerEntity.executeQuery(
                """SELECT DISTINCT a.actionName
                   FROM AuditLogControllerEntity AS a
                   WHERE a.controllerName='${chosenController}' """)

        render(template: "auditLogActions",
                model: [actions:actions, chosenController:chosenController])
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private def likeExpression(def value) {
        return """%${value}%"""
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private Date createDateFromParams(String year, String month, String day,
                                      String hour, String minute) {
        log.debug """Year: ${year} - ${month} - ${day} - ${hour} - ${minute} """

        def cal

        if (year && month && day && hour && minute) {
            cal = new GregorianCalendar()
            cal.set(Calendar.YEAR,Integer.parseInt(year))
            cal.set(Calendar.MONTH,Integer.parseInt(month) - 1)
            cal.set(Calendar.DAY_OF_MONTH,Integer.parseInt(day))
            cal.set(Calendar.HOUR_OF_DAY,Integer.parseInt(hour))
            cal.set(Calendar.MINUTE,Integer.parseInt(minute))
        }

        if (cal != null) {
            log.debug "Returning: ${cal?.getTime()}"
        }

        return cal?.getTime()
    }

    @Secured([AuditLogPermissionName.AUDITLOG_READ])
    def search() {
        // Extract search criteria
        log.debug "Starting search: " + params

        def ssn = params.ssn
        def patientId = (params.patientId) ? params.patientId as Long : null
        def action = params.chosenAction
        def controller = params.chosenController

        Integer offset = (params.offset) ? params.offset.toInteger() : null
        Integer max = (params.max) ? params.max.toInteger() : null

        def (Date fromDate, Date toDate) = setToAndFromDate(params)
        boolean exceptionOccurred = setExceptionOccurred(params)

        log.debug "Searching for ssn: ${ssn} " +
                "patient id: ${patientId} " +
                "action: ${action} " +
                "from: ${fromDate} to ${toDate} " +
                "exception occured?: ${exceptionOccurred}"

        def query = "from AuditLogEntry as a, " +
                "AuditLogControllerEntity as ae " +
                "where a.action = ae.id and "
        def criteria = [:]

        query = addDatesToCriteria(fromDate, toDate, query, criteria)
        query = addSSNToCriteria(ssn, query, criteria)
        query = addPatientIdToCriteria(patientId, query, criteria)
        query = addActionToCriteria(action, query, criteria)
        query = addControllerToCriteria(controller, query, criteria)

        if (action || controller) {
            query += "and a.action = ae.id "
        }

        query = addExceptionOccurredToCriteria(exceptionOccurred, query, criteria)
        LinkedHashMap<String, Integer> queryOptions = setQueryOptions(offset, max)

        log.debug "Executing: " + query + " with criteria: " + criteria

        def totalCount = AuditLogEntry.findAll(query,criteria).size()
        def list = AuditLogEntry.findAll(query,criteria,queryOptions)

        def entries = []

        for (item in list) {
            entries << item[0]
        }

        entries.each { entry -> checkForAnonymousUser(entry) }

        List controllers = AuditLogControllerEntity.executeQuery(
                "SELECT DISTINCT a.controllerName FROM AuditLogControllerEntity as a")
        List actions = AuditLogControllerEntity.executeQuery(
                "SELECT DISTINCT a.actionName FROM AuditLogControllerEntity AS a WHERE a.controllerName='${controller}'")

        [auditLogEntryInstanceList: entries,
         auditLogEntryInstanceTotal: totalCount,
         toDate: toDate,
         fromDate: fromDate,
         ssn: ssn,
         patientId: patientId,
         exceptionOccurred: exceptionOccurred,
         chosenAction: action,
         chosenController: controller,
         controllers: controllers,
         actions: actions]
    }

    private List setToAndFromDate(Map params) {
        DateFormat dateFormat = new SimpleDateFormat("EEE MMM dd kk:mm:ss z yyyy", Locale.ENGLISH);

        Date fromDate
        if (params.fromDate) {
            fromDate = params.fromDate instanceof Date ? params.fromDate : dateFormat.parse(params.fromDate as String)
        } else {
            fromDate = createDateFromParams(params.fromDate_year,
                    params.fromDate_month, params.fromDate_day,
                    params.fromDate_hour, params.fromDate_minute)
        }

        Date toDate
        if (params.toDate) {
            toDate = params.toDate instanceof Date ? params.toDate : dateFormat.parse(params.toDate as String)
        } else {
            toDate = createDateFromParams(params.toDate_year,
                    params.toDate_month, params.toDate_day,
                    params.toDate_hour, params.toDate_minute)
        }

        if (!fromDate) {
            def cal = new GregorianCalendar()
            cal.add(Calendar.WEEK_OF_YEAR, -1)
            fromDate = cal.getTime()
            log.debug "Setting from date " + fromDate
        }

        log.debug "fromDate: ${fromDate}"
        log.debug "toDate: ${toDate}"
        [fromDate, toDate]
    }

    private static boolean setExceptionOccurred(params) {
        def exceptionOccurred = Boolean.FALSE

        if (params.exceptionOccurred.equals("on")) {
            exceptionOccurred = Boolean.TRUE
        }

        exceptionOccurred
    }

    private static String addDatesToCriteria(Date fromDate, Date toDate,
                                             String query, LinkedHashMap criteria) {
        if (fromDate) {
            query += "a.startTime > :fromDate "
            criteria.put("fromDate", fromDate.time)
        }

        if (toDate) {
            query += "and a.endTime < :toDate "
            criteria.put("toDate", toDate.time)
        }
        query
    }

    private String addSSNToCriteria(ssn, String query, LinkedHashMap criteria) {
        if (ssn) {
            ssn.replaceAll("-", "")
            ssn.replaceAll(" ", "")

            if (((String) ssn).length() > 6) {
                def longerSsn = ssn[0..5] + "-" + ssn[6..((String) ssn).length() - 1]
                query += "and (a.patientCpr like :ssn or a.patientCpr like :longerSsn) "
                criteria.put("ssn", likeExpression(ssn))
                criteria.put("longerSsn", likeExpression(longerSsn))

            } else {
                // Good case
                query += "and a.patientCpr like :ssn "
                criteria.put("ssn", likeExpression(ssn))
            }
        }
        query
    }

    private String addPatientIdToCriteria(Long patientId, String query, LinkedHashMap criteria) {
        if (patientId) {
            query += "and a.patientId like :patientId "
            criteria.put("patientId", likeExpression(patientId))
        }
        query
    }

    private String addActionToCriteria(action, String query, LinkedHashMap criteria) {
        if (action) {
            query += "and ae.actionName like :action "
            criteria.put("action", likeExpression(action))
        }
        query
    }

    private String addControllerToCriteria(controller, String query, LinkedHashMap criteria) {
        if (controller) {
            query += "and ae.controllerName like :controller "
            criteria.put("controller", likeExpression(controller))
        }
        query
    }

    private static String addExceptionOccurredToCriteria(Boolean exceptionOccurred,
                                                         String query, LinkedHashMap criteria) {
        if (exceptionOccurred) {
            query += "and exception = :exceptionOccurred "
            criteria.put("exceptionOccurred", exceptionOccurred)
        }
        query
    }

    private static LinkedHashMap<String, Integer> setQueryOptions(Integer offset, Integer max) {
        def queryOptions = [max: 10]
        if (offset) {
            queryOptions["offset"] = offset
        }

        if (max) {
            queryOptions["max"] = max
        }
        queryOptions
    }

    private void checkForAnonymousUser(AuditLogEntry auditLogEntryInstance) {
        if (auditLogEntryInstance.authority == GRAILS_ANONYMOUS_USER) {
            auditLogEntryInstance.authority = message(
                    code: 'auditlog.entry.default.anonymousUser',
                    default: auditLogEntryInstance.authority);
        }
    }
}