package com.sar.web.handler;

import com.service.GroupService;
import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import com.sar.server.Main;
import com.sar.model.Group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;
import java.time.Instant;
import java.io.IOException;
import java.nio.charset.StandardCharsets; // Needed if using UTF-8 anywhere specific here

public class ApiHandler extends AbstractRequestHandler  {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);
    private final GroupService groupService;

    public ApiHandler(GroupService groupService) {
        this.groupService = groupService;
    }

    /** Handles GET requests - displays the form and current groups */
    @Override
    protected void handleGet(Request request, Response response) {
        logger.debug("Processing GET request for API");
        try {
            // Default values for an initial GET request
            String groupNumber = "";
            String n1 = "", na1 = "", n2 = "", na2 = "";
            String lastUpdate = "N/A";
            int numberTimes = 0;
            boolean counter = false;

            // Prepare html page
            String html = make_Page(
                request.getClientAddress(),
                request.getClientPort(),
                request.headers.getHeaderValue("User-Agent"),
                groupNumber, numberTimes, n1, na1, n2, na2,
                counter, lastUpdate,
                null // No status message for initial GET
            );

            response.setCode(ReplyCode.OK);
            response.setTextHeaders(html); // Uses UTF-8 by default now

        } catch (Exception e) {
            logger.error("Error processing GET request in ApiHandler", e);
            response.setError(ReplyCode.BADREQ, request.version);
        }
    }

    /** Handles POST method for saving or deleting group data */
    @Override
    protected void handlePost(Request request, Response response) {
        logger.info("Processing POST request for API");
        try {
            Properties fields = request.getPostParameters();
            String groupNumber = fields.getProperty("Grupo", "").trim();
            String[] numbers = new String[Main.GROUP_SIZE];
            String[] names = new String[Main.GROUP_SIZE];

            // Store submitted form values
            numbers[0] = fields.getProperty("Num1", "").trim();
            names[0] = fields.getProperty("Nome1", "").trim();
            numbers[1] = fields.getProperty("Num2", "").trim();
            names[1] = fields.getProperty("Nome2", "").trim();
            boolean counterEnabled = fields.getProperty("Contador") != null;

            // Check which button was pressed
            boolean submitButton = fields.getProperty("BotaoSubmeter") != null;
            boolean deleteButton = fields.getProperty("BotaoApagar") != null;

            String statusMessage = "";
            int accessCount = 0;
            String lastUpdate = "N/A";
            boolean currentCounterStatus = counterEnabled;

            if (groupNumber.isEmpty() && (submitButton || deleteButton)) {
                statusMessage = "Group number cannot be empty for Submit or Delete.";
                logger.warn("POST request rejected: Group number is empty for Submit/Delete.");
                groupNumber = "";
                numbers[0] = numbers[1] = "";
                names[0] = names[1] = "";
                currentCounterStatus = false;

            } else if (deleteButton) {
                // --- Delete Logic ---
                logger.info("Delete button pressed for group: {}", groupNumber);
                if (groupService.groupExists(groupNumber)) {
                    groupService.deleteGroup(groupNumber);
                    statusMessage = "Group " + groupNumber + " deleted successfully.";
                    logger.info("Group {} deleted.", groupNumber);
                    // Clear fields after successful deletion
                    groupNumber = "";
                    numbers[0] = numbers[1] = "";
                    names[0] = names[1] = "";
                    currentCounterStatus = false;
                    accessCount = 0;
                    lastUpdate = "N/A";
                } else {
                    statusMessage = "Group " + groupNumber + " not found for deletion.";
                    logger.warn("Attempted to delete non-existent group: {}", groupNumber);
                    accessCount = 0;
                    lastUpdate = "N/A";
                }
                // --- End Delete Logic ---

            } else if (submitButton) {
                // --- Submit/Save Logic ---
                logger.info("Submit button pressed for group: {}", groupNumber);
                groupService.saveGroup(groupNumber, numbers, names, counterEnabled);
                statusMessage = "Group " + groupNumber + " saved/updated successfully.";
                logger.info("Group {} saved/updated.", groupNumber);

                // Fetch the saved/updated group to display current info
                Group savedGroup = groupService.getGroup(groupNumber);
                if (savedGroup != null) {
                   accessCount = savedGroup.getAccessCount();
                   lastUpdate = savedGroup.getLastUpdate();
                   currentCounterStatus = savedGroup.isCounter();
                } else {
                    // Should not happen after a save, but handle defensively
                    lastUpdate = Instant.now().toString();
                    accessCount = 0;
                    currentCounterStatus = counterEnabled;
                }
                // --- End Submit/Save Logic ---

            } else {
                 // Handle form submission without explicit button (e.g., Enter key)
                 logger.warn("Received POST request for group {} without explicit Submit or Delete action. Reloading form.", groupNumber);
                 if (groupNumber != null && !groupNumber.isEmpty() && groupService.groupExists(groupNumber)) {
                      // If group exists, show its current data
                      Group existingGroup = groupService.getGroup(groupNumber);
                      accessCount = existingGroup.getAccessCount();
                      lastUpdate = existingGroup.getLastUpdate();
                      for (int i = 0; i < Main.GROUP_SIZE; i++) {
                          Group.Member m = existingGroup.getMember(i);
                          numbers[i] = (m != null) ? m.getNumber() : "";
                          names[i] = (m != null) ? m.getName() : "";
                      }
                      currentCounterStatus = existingGroup.isCounter();
                 } else {
                      // If group doesn't exist or number empty, show submitted/empty values
                      accessCount = 0;
                      lastUpdate = "N/A";
                      // Keep submitted numbers/names/counterEnabled
                 }
            }

            // Prepare and send the response page
            String html = make_Page(
                request.getClientAddress(),
                request.getClientPort(),
                request.headers.getHeaderValue("User-Agent"),
                groupNumber,
                accessCount,
                numbers[0], names[0],
                numbers[1], names[1],
                currentCounterStatus,
                lastUpdate,
                statusMessage
            );

            response.setCode(ReplyCode.OK);
            response.setTextHeaders(html); // Uses UTF-8 by default now

            // NOTE: Cache-Control headers removed in this version

        } catch (Exception e) {
            logger.error("Error processing POST request in ApiHandler", e);
            response.setError(ReplyCode.BADREQ, request.version);
        }
    }

   /**
    * Prepares the web page that is sent as reply to the API call.
    */
   private String make_Page(String ip, int port, String userAgent, String group,
           int numberTimes, String n1, String na1, String n2, String na2,
           boolean count, String lastUpdate, String statusMessage) {

       logger.debug("Entering make_Page for group: {}", group);

       // Draw "lucky" numbers
       int[] set1 = draw_numbers(50, 5);
       int[] set2 = draw_numbers(9, 2);

       StringBuilder html = new StringBuilder();
       // --- HTML Header ---
       html.append("<!doctype html>\r\n");
       html.append("<html class=\"no-js\" lang=\"en\">\r\n");
       html.append("<head>\r\n");
       html.append("<meta charset=\"utf-8\" />\r\n"); // Use utf-8 for broader compatibility
       html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\r\n");
       html.append("<title>SAR 24/25</title>\r\n");
       html.append("<link rel=\"stylesheet\" href=\"css/foundation.css\" />\r\n");
       html.append("<script src=\"js/modernizr.js\"></script>\r\n");
       html.append("</head>\r\n<body>\r\n");

       // --- Page Header and Nav ---
       html.append("<div class=\"row\">\r\n");
       html.append("  <div class=\"medium-12 columns\">\r\n");
       html.append("    <p><img src=\"img/header.png\" /></p>\r\n");
       html.append("  </div>\r\n");
       html.append("  <div class=\"medium-12 columns\" >\r\n");
       html.append("    <div class=\"contain-to-grid\">\r\n");
       html.append("      <nav class=\"top-bar\" data-topbar>\r\n");
       html.append("        <ul class=\"title-area\">\r\n");
       html.append("          <li class=\"name\">\r\n");
       html.append("            <h1><a href=\"/SarAPI\">S.A.R 24/25</a></h1>\r\n");
       html.append("          </li>\r\n");
       html.append("          <li class=\"toggle-topbar menu-icon\"><a href=\"#\"><span>menu</span></a></li>\r\n");
       html.append("        </ul>\r\n");
       html.append("        <section class=\"top-bar-section\">\r\n");
       html.append("          <ul class=\"right\">\r\n");
       html.append("            <li><a href=\"/SarAPI\">API request</a></li>\r\n");
       html.append("          </ul>\r\n");
       html.append("        </section>\r\n");
       html.append("      </nav>\r\n");
       html.append("    </div>\r\n");
       html.append("  </div>\r\n");
       html.append("</div>\r\n");

       // --- Main Content ---
       html.append("<div class=\"row\">\r\n");
       html.append("  <div class=\"medium-12 columns\">\r\n");
       html.append("    <div class=\"panel\">\r\n");

       // --- Status Message ---
       if (statusMessage != null && !statusMessage.isEmpty()) {
            html.append("<div class=\"row\"><div class=\"medium-12 columns\">");
            html.append("<div data-alert class=\"alert-box success radius\">"); // Use Foundation alert box
            html.append(statusMessage);
            html.append("<a href=\"#\" class=\"close\">&times;</a>");
            html.append("</div>");
            html.append("</div></div>\r\n");
       }

       // --- Group Info Display ---
       if (group != null && !group.isEmpty() && numberTimes >= 0) {
            html.append("<p>Group: ").append(group).append(" | Access Count: ").append(numberTimes)
                .append(" | Last Update: <font color=\"#0000ff\">").append(lastUpdate != null ? lastUpdate : "N/A").append("</font></p>\r\n");
       } else {
            html.append("<p>Enter group details below or select from the list.</p>\r\n");
       }

       // --- Input Form ---
       html.append("<form method=\"post\" action=\"SarAPI\">\r\n");
       html.append("<h3>Group Data</h3>\r\n");
       html.append("<p>Group <input name=\"Grupo\" size=\"10\" type=\"text\" value=\"").append(group != null ? group : "").append("\"></p>\r\n");
       for (int i = 0; i < Main.GROUP_SIZE; i++) {
           String currentNum = "";
           String currentName = "";
           if (i == 0) { currentNum = n1; currentName = na1; }
           else if (i == 1) { currentNum = n2; currentName = na2; }
           // Add more else if for GROUP_SIZE > 2 if needed

           html.append("<p>Number ").append(i + 1).append(" <input name=\"Num").append(i + 1)
               .append("\" size=\"10\" type=\"text\" value=\"").append(currentNum != null ? currentNum : "").append("\">");
           html.append(" Name ").append(i + 1).append(" <input name=\"Nome").append(i + 1)
               .append("\" size=\"60\" type=\"text\" value=\"").append(currentName != null ? currentName : "").append("\"></p>\r\n");
       }
       html.append("<p><input name=\"Contador\" type=\"checkbox\" value=\"ON\"").append(count ? " checked" : "").append("> Counter</p>\r\n");
       html.append("<p><input value=\"Submit\" name=\"BotaoSubmeter\" type=\"submit\"> ");
       html.append("<input value=\"Delete\" name=\"BotaoApagar\" type=\"submit\"> ");
       html.append("<input value=\"Clear\" type=\"reset\" name=\"BotaoLimpar\"></p>\r\n");
       html.append("</form>\r\n");

       // --- Registered Groups Table ---
       html.append("<h3>Registered groups</h3>\r\n");
       try {
           logger.debug("Calling groupService.generateGroupHtml()...");
           html.append(groupService.generateGroupHtml());
           logger.debug("Finished groupService.generateGroupHtml().");
       } catch (Exception e) {
           logger.error("Error generating group HTML table within make_Page", e);
           html.append("<p>Error loading group list.</p>");
       }

       // --- Lucky Numbers Example ---
       html.append("<h3>Example of dynamic content :-)</h3>\r\n");
       html.append("<p>If you want to waste some money, here are some suggestions for the next ");
       html.append("<a href=\"https://www.jogossantacasa.pt/web/JogarEuromilhoes/?\">Euromillions</a>: ");
       for (int i = 0; i < 5; i++)
           html.append(i == 0 ? "" : " ").append("<font color=\"#00ff00\">")
               .append(minimum(set1, 50)).append("</font>");
       html.append(" + <font color=\"#800000\">").append(minimum(set2, 9))
           .append("</font> <font color=\"#800000\">").append(minimum(set2, 9))
           .append("</font></p>\r\n");

       // --- HTML Footer ---
       html.append("    </div>\r\n"); // Close panel
       html.append("  </div>\r\n"); // Close columns
       html.append("</div>\r\n"); // Close row
       html.append("<footer class=\"row\">\r\n");
       html.append("  <div class=\"medium-12 columns\">\r\n");
       html.append("    <hr />\r\n");
       html.append("    <p>© DEE - FCT/UNL.</p>\r\n");
       html.append("  </div>\r\n");
       html.append("</footer>\r\n");

       // --- Scripts ---
       html.append("<script src=\"js/jquery.js\"></script>\r\n");
       html.append("<script src=\"js/foundation.min.js\"></script>\r\n");
       html.append("<script src=\"js/foundation/foundation.topbar.js\"></script>\r\n");
       html.append("<script src=\"js/foundation/foundation.alert.js\"></script>\r\n");
       html.append("<script>\r\n");
       html.append("  $(document).foundation();\r\n");
       html.append("</script>\r\n");
       html.append("</body>\r\n</html>");

       logger.debug("Exiting make_Page for group: {}. HTML length: {}", group, html.length());
       return html.toString();
   }


    // Helper method to draw lucky numbers
    private int[] draw_numbers(int max, int k) {
        int[] vec = new int[k];
        int j;
        Random rnd = new Random(System.currentTimeMillis());
        for (int i = 0; i < k; i++) {
            do {
                vec[i] = rnd.nextInt(max) + 1;
                for (j = 0; j < i; j++) {
                    if (vec[j] == vec[i]) break;
                }
            } while((i != 0) && (j < i));
        }
        return vec;
    }

    // Helper method to find and mark minimum in lucky numbers
    private int minimum(int[] vec, int max) {
        int min = max + 1, n = -1;
        for (int i = 0; i < vec.length; i++) {
            if (vec[i] < min) {
                n = i;
                min = vec[i];
            }
        }
        if (n == -1) {
            logger.error("Internal error in API.minimum");
            return max + 1;
        }
        vec[n] = max + 1; // Mark position as used
        return min;
    }

} // End of class
