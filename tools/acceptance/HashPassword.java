import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class HashPassword {
    public static void main(String[] args) throws Exception {
        String password = new BufferedReader(new InputStreamReader(System.in)).readLine();
        System.out.print(new BCryptPasswordEncoder(10).encode(password));
    }
}
