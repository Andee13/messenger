package ua.sumdu.j2se.Birintsev.common.utill;

import org.jetbrains.annotations.NotNull;
import ua.sumdu.j2se.Birintsev.client.UserData;

import java.io.*;

public class IO {
    private IO(){

    }

    public static void write(@NotNull UserData userData, File file) throws IOException{
        try(BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file));) {
            bufferedWriter.write(userData.getLogin());
            bufferedWriter.write(userData.getPassword());

            if(userData.getInfo() != null) {
                bufferedWriter.write(userData.getInfo());
            } else {
                bufferedWriter.write(new StringBuilder(userData.getLogin()).append(" has not set the info yet").toString());
            }
            bufferedWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    // TODO logging exceptions
    public static void read(UserData userData, File file) throws IOException {
        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(file))){
            String login = bufferedReader.readLine();
            String passowrd = bufferedReader.readLine();

            StringBuilder info = new StringBuilder();
            int c = bufferedReader.read();
            while (c != -1) {
                info.append((char) c);
                c = bufferedReader.read();
            }
            userData = new UserData(login, passowrd);
            userData.setInfo(info.toString());
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }
}
