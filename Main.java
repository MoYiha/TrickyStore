public class Main {
    public static void main(String[] args) {
        String s = "2023-12-05";
        String[] l = s.split("-");
        System.out.println(Integer.parseInt(l[0]) * 100 + Integer.parseInt(l[1]));
    }
}
