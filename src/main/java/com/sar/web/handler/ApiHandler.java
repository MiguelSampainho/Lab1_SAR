package com.sar.web.handler;

import com.service.GroupService;
import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import com.sar.server.Main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;

public class ApiHandler extends AbstractRequestHandler  {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);
    private final GroupService groupService;

    public ApiHandler(GroupService groupService) {
        this.groupService = groupService;
    }

    /** Select a subset of 'k' number of a set of numbers raging from 1 to 'max' */    
    private int[] draw_numbers(int max, int k) {
        int[] vec = new int[k];
        int j;
        
        Random rnd = new Random(System.currentTimeMillis());
        for (int i = 0; i < k; i++) {
            do {
                vec[i] = rnd.nextInt(max) + 1;
                for (j = 0; j < i; j++) {
                    if (vec[j] == vec[i])
                        break;
                }
            } while((i != 0) && (j < i));
        }
        return vec;
    }
    
    /** Selects the minimum number in the array */
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
        vec[n] = max + 1;  // Mark position as used
        return min;
    }

    /** Prepares the web page that is sent as reply to the API call */
    private String make_Page(String ip, int port, String tipo, String group, 
            int numberTimes, String n1, String na1, String n2, String na2
            ,boolean count, String lastUpdate) {
        
        // Draw "lucky" numbers
        int[] set1 = draw_numbers(50, 5);
        int[] set2 = draw_numbers(9, 2);
        
        // Prepare string html with web page
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\r\n");
        html.append("<html class=\"no-js\" lang=\"en\">\r\n");
        html.append("<head>\r\n");
        html.append("<meta charset=\"utf-8\" />\r\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\r\n");
        html.append("<title>SAR 24/25</title>\r\n");
        html.append("<link rel=\"stylesheet\" href=\"css/foundation.css\" />\r\n");
        html.append("<script src=\"js/modernizr.js\"></script>\r\n");
        html.append("</head>\r\n<body>\r\n");

        // Header and Nav
        html.append("<div class=\"row\">\r\n");
        html.append("  <div class=\"medium-12 columns\">\r\n");
        html.append("    <p><img src=\"img/header.png\" /></p>\r\n");
        html.append("  </div>\r\n");
        html.append("  <div class=\"medium-12 columns\">\r\n");
        html.append("    <div class=\"contain-to-grid\">\r\n");
        html.append("      <nav class=\"top-bar\" data-topbar>\r\n");
        html.append("        <ul class=\"title-area\">\r\n");
        html.append("          <li class=\"name\">\r\n");
        html.append("            <h1><a href=\"SarAPI\">S.A.R 24/25</a></h1>\r\n");
        html.append("          </li>\r\n");
        html.append("          <li class=\"toggle-topbar menu-icon\"><a href=\"#\"><span>menu</span></a></li>\r\n");
        html.append("        </ul>\r\n");
        html.append("        <section class=\"top-bar-section\">\r\n");
        html.append("          <ul class=\"right\">\r\n");
        html.append("            <li><a href=\"SarAPI\">API request</a></li>\r\n");
        html.append("          </ul>\r\n");
        html.append("        </section>\r\n");
        html.append("      </nav>\r\n");
        html.append("    </div>\r\n");
        html.append("  </div>\r\n");
        html.append("</div>\r\n");

        // Main Content Section
        html.append("<div class=\"row\">\r\n");
        html.append("  <div class=\"medium-12 columns\">\r\n");
        html.append("    <div class=\"panel\">\r\n");

        // Your existing dynamic content
        if (count) {
            html.append("<p>The group:" + (group.length()>0 ? (group) : "?") + " was already updated ");
            html.append("" + numberTimes + " times.</p>\r\n");
        }
        html.append("<p>The last access to this server by this user was in: ");
        html.append("<font color=\"#0000ff\">" + lastUpdate + "</font></p>\r\n");

        // Form
        html.append("<form method=\"post\" action=\"sarAPI\">\r\n");
        html.append("<h3>Group Data</h3>\r\n");
        html.append("<p>Group <input name=\"Grupo\" size=\"2\" type=\"text\"" +
                (group.length()>0 ? " value=\""+group+"\"": "") + "></p>\r\n");
        html.append("<p>Number <input name=\"Num1\" size=\"5\" type=\"text\"" +
                (n1.length()>0 ? " value="+n1 : "") + ">\r\n" +
                "Name <input name=\"Nome1\" size=\"80\" type=\"text\"" +
                (na1.length()>0 ? " value="+na1 : "") + "></p>\r\n");
        html.append("<p>Number <input name=\"Num2\" size=\"5\" type=\"text\"" +
                (n2.length()>0 ? " value="+n2 : "") + ">\r\n" +
                "Name <input name=\"Nome2\" size=\"80\" type=\"text\"" +
                (na2.length()>0 ? " value="+na2 : "") + "></p>\r\n");
        html.append("<p><input name=\"Contador\"" + (count?" checked=\"checked\"":"") + " value=\"ON\" type=\"checkbox\">Counter</p>\r\n");
        html.append("<p><input value=\"Submit\" name=\"BotaoSubmeter\" type=\"submit\">");
        html.append("<input value=\"Delete\" name=\"BotaoApagar\" type=\"submit\">");
        html.append("<input value=\"Clear\" type=\"reset\" name=\"BotaoLimpar\"></p>\r\n");
        html.append("</form>\r\n");

        // Registered groups table
        html.append("<h3>Registered groups</h3>\r\n");
        html.append(groupService.generateGroupHtml());

        // Lucky numbers
        html.append("<h3>Example of dynamic content :-)</h3>\r\n");
        html.append("<p>If you want to waste some money, here are some suggestions for the next ");
        html.append("<a href=\"https://www.jogossantacasa.pt/web/JogarEuromilhoes/?\">Euromillions</a>: ");
        for (int i = 0; i < 5; i++)
            html.append(i == 0 ? "" : " ").append("<font color=\"#00ff00\">")
                .append(minimum(set1, 50)).append("</font>");
        html.append(" + <font color=\"#800000\">").append(minimum(set2, 9))
            .append("</font> <font color=\"#800000\">").append(minimum(set2, 9))
            .append("</font></p>\r\n");

        html.append("    </div>\r\n"); // Close panel
        html.append("  </div>\r\n"); // Close columns
        html.append("</div>\r\n"); // Close row

        // Footer
        html.append("<footer class=\"row\">\r\n");
        html.append("  <div class=\"medium-12 columns\">\r\n");
        html.append("    <hr />\r\n");
        html.append("    <p>© DEE - FCT/UNL.</p>\r\n");
        html.append("  </div>\r\n");
        html.append("</footer>\r\n");

        // Scripts
        html.append("<script src=\"js/jquery.js\"></script>\r\n");
        html.append("<script src=\"js/foundation.min.js\"></script>\r\n");
        html.append("<script src=\"js/foundation/foundation.topbar.js\"></script>\r\n");
        html.append("<script>\r\n");
        html.append("  $(document).foundation();\r\n");
        html.append("</script>\r\n");

        html.append("</body>\r\n</html>");

        return html.toString();
    }

    @Override
    protected void  handleGet(Request request, Response response) {
        logger.debug("Processing GET request");
        try {
            String group = "";
            String nam1 = "", n1 = "", nam2 = "", n2 = "";
            String lastUpdate = "";
            int numberTimes = -1;

             /**
         *      This part must check if the browser is sending the sarCookie
         *      If it is, it must deliver a web page with the last group introduced by the user
         *      Otherwise, the fields must be empty
         */
            logger.debug("Cookies ignored in API GET");
            // Prepare html page
            String html = make_Page(
                request.getClientAddress(),
                request.getClientPort(),
                request.headers.getHeaderValue("User-Agent"),
                group, numberTimes, n1, nam1, n2, nam2, 
                false, lastUpdate
            );

            // Prepare answer
            response.setCode(ReplyCode.OK);
            response.setTextHeaders(html);
            return;

        } catch (Exception e) {
            logger.error("Error processing GET request", e);
            response.setError(ReplyCode.BADREQ, request.version);
        }
    }

    /** Runs POST method */
    @Override
    protected void handlePost(Request request, Response response) {
        logger.debug("Processing POST request");
        try {
            Properties fields = request.getPostParameters();
            String group = fields.getProperty("Grupo", "");
            String[] numbers = new String[Main.GROUP_SIZE];
            String[] names = new String[Main.GROUP_SIZE];
            
            // Get member information from form
            for (int i = 0; i < Main.GROUP_SIZE; i++) {
                numbers[i] = fields.getProperty("Num" + (i + 1), "");
                names[i] = fields.getProperty("Nome" + (i + 1), "");
            }
            
            boolean submitButton = (fields.getProperty("BotaoSubmeter") != null);
            boolean deleteButton = (fields.getProperty("BotaoApagar") != null);
            boolean counter = (fields.getProperty("Contador") != null);
            
            // Create or delete group in the database  
    

            String lastUpdate = "";
            int cnt = -1;
            /*Check if group is emmpty
             * And if the user pressed the delete button
             * Or the submit button to either delete or save the group
             * ckeck the access count and last update
             */

            logger.info("Button: " + (submitButton ? "Submit" : "") + (deleteButton ? "Delete" : "") + "\n");
            logger.info("POST not completed in API ");
            // Prepare html page
            String html = make_Page(
                request.getClientAddress(),
                request.getClientPort(),
                request.headers.getHeaderValue("User-Agent"),
                group, cnt,
                numbers[0], names[0],
                numbers[1], names[1],
                counter, lastUpdate
            );

            // Prepare answer
            response.setCode(ReplyCode.OK);
            response.setTextHeaders(html);
            
            // Set cookie if group exists
            return ;

        } catch (Exception e) {
            logger.error("Error processing POST request", e);
            response.setError(ReplyCode.BADREQ, request.version);
            return ;
        }
    }
}