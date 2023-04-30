import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Declaring a WebServlet called SingleMovieServlet, which maps to url "/api/single-movie"
@WebServlet(name = "SingleMovieServlet", urlPatterns = "/api/single-movie")
public class SingleMovieServlet extends HttpServlet{
    private static final long serialVersionUID = 2L;
    // Create a dataSource which registered in web.xml
    private DataSource dataSource;

    public void init(ServletConfig config) {
        try {
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedb");
        } catch (NamingException e) {
            e.printStackTrace();
        }
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
     * response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json"); // Response mime type

        // Retrieve parameter id from url request.
        String id = request.getParameter("id");

        // The log message can be found in localhost log
        request.getServletContext().log("getting id: " + id);

        // Output stream to STDOUT
        PrintWriter out = response.getWriter();

        // Get a connection from dataSource and let resource manager close the connection after usage.
        try (Connection conn = dataSource.getConnection()) {
            // Get a connection from dataSource

            // Construct a query with parameter represented by "?"
            String query = "with selectedStars as (SELECT x.id, x.name, total\n" +
                    "FROM stars x \n" +
                    "LEFT JOIN (SELECT starId, COUNT(*) total FROM stars_in_movies GROUP BY starId) y ON y.starId = x.id\n" +
                    "ORDER BY total DESC, id), \n" +
                    "SelectedTitleANDRating AS (\n" +
                    "SELECT m.id, m.title, m.year, m.director, r.rating\n" +
                    "FROM movies m left join ratings r ON m.id = r.movieId\n" +
                    "WHERE m.id LIKE ?\n" +
                    "),\n" +
                    "movie_genres as(\n" +
                    "SELECT gm.movieId, GROUP_CONCAT(g.name order by g.name asc) as genres \n" +
                    "FROM genres_in_movies gm\n" +
                    "INNER JOIN genres g ON g.id = gm.genreId where g.id like '%' \n" +
                    "GROUP BY movieId\n" +
                    ")\n" +
                    "SELECT M.title,M.year,M.director,M.id as movieId,M.rating,GM.genres as genres,star_list.starId as starId, star_list.starName as starName\n" +
                    "FROM SelectedTitleANDRating as M inner join movie_genres as GM on GM.movieId = M.id \n" +
                    "inner join (select sm.movieId, GROUP_CONCAT(s.id order by s.total desc, s.name) as starId,\n" +
                    "GROUP_CONCAT(s.name order by s.total desc, s.name) as starName from stars_in_movies sm \n" +
                    "INNER JOIN selectedStars as s ON s.id = sm.starId where s.name like '%' GROUP BY \n" +
                    "movieId) as star_list on star_list.movieId = M.id\n" +
                    "WHERE year like '%' AND director like '%'\n";

            // Declare our statement
            PreparedStatement statement = conn.prepareStatement(query);

            // Set the parameter represented by "?" in the query to the id we get from url,
            // num 1 indicates the first "?" in the query
            statement.setString(1, id);

            // Perform the query
            ResultSet rs = statement.executeQuery();

            JsonArray jsonArray = new JsonArray();

            // Iterate through each row of rs
            while (rs.next()) {
                String movieId = rs.getString("movieId");
                String movieTitle = rs.getString("M.title");
                String movieYear = rs.getString("M.year");
                String movieDirector = rs.getString("M.director");
                String starsId = rs.getString("starId");
                String starsName = rs.getString("starName");
                String movieGenres = rs.getString("genres");
                String movieRating;
                if(rs.getString("M.rating") != null){
                    movieRating = rs.getString("M.rating");
                }
                else{
                    movieRating = "N/A";
                }

                // Create a JsonObject based on the data we retrieve from rs

                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("genres", movieGenres);
                jsonObject.addProperty("movie_id", movieId);
                jsonObject.addProperty("movie_title", movieTitle);
                jsonObject.addProperty("movie_year", movieYear);
                jsonObject.addProperty("movie_director", movieDirector);
                jsonObject.addProperty("starID", starsId);
                jsonObject.addProperty("starsName", starsName);
                jsonObject.addProperty("movie_genres", movieGenres);
                jsonObject.addProperty("movie_rating", movieRating);

                jsonArray.add(jsonObject);
            }
            rs.close();
            statement.close();

            // Write JSON string to output
            out.write(jsonArray.toString());
            // Set response status to 200 (OK)
            response.setStatus(200);

        } catch (Exception e) {
            // Write error message JSON object to output
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("errorMessage", e.getMessage());
            out.write(jsonObject.toString());

            // Log error to localhost log
            request.getServletContext().log("Error:", e);
            // Set response status to 500 (Internal Server Error)
            response.setStatus(500);
        } finally {
            out.close();
        }

        // Always remember to close db connection after usage. Here it's done by try-with-resources

    }

}
