package sample.tomcat.condition;

public class MyMacService {
    private int age;
    private String name;

    public MyMacService(int age, String name) {
        this.age = age;
        this.name = name;
    }

    @Override
    public String toString() {
        return "MyMacService{" +
                "age=" + age +
                ", name='" + name + '\'' +
                '}';
    }
}
