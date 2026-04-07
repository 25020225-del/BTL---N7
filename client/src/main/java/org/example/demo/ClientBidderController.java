package org.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import model.Bidder;
import model.User;

import java.io.File;

public class ClientBidderController {
    String st = "";
    public  ClientBidderController(String st){
        this.st = st;
    }

    public static void main(String[] args){
        Bidder user = new Bidder(new User("Bidder01","khoadeptrai","123456","Ngo Vu Dinh Khoa"));
        try{
            File file = new File("data.json");
            ObjectMapper mapper = new ObjectMapper();

            mapper.writeValue(file, user);

        }
        catch (Exception e){}
        finally {
            System.out.println("Hell nah");
        }
    }
}
