package client;

import common.ExamStatusServer;
import common.StatusClient;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

import static java.lang.Thread.sleep;


public class Client {
    private static Registry registry;
    private static ExamStatusImpl exam_status_impl;

    public static void main(String[] args) {
        try {
            if (exam_status_impl == null) {
                exam_status_impl = new ExamStatusImpl();
            }
            if (registry == null) {
                registry = LocateRegistry.getRegistry();
            }
            StatusClient status_client = (StatusClient) registry.lookup("Hello");
            String idStudent = startExam(status_client, exam_status_impl);
            if (idStudent != null) {
                answerExam(status_client, exam_status_impl, idStudent);
                sleep(5);
                finishExam();
            }
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    // Function to be executed when the user finish the exam or the professor say that the exam has finished
    private static void finishExam() {
        System.out.println("\n-----------------------------------------------------------");
        System.out.println("\nTIME OVER, THE EXAM HAS FINISHED!!");
        Float score_exact = ((float) 10 *(float)exam_status_impl.getCorrectAnswers())/(float)exam_status_impl.getTotalQuestions();
        System.out.println("\nYou have a total score of " + exam_status_impl.getCorrectAnswers() + "/" + exam_status_impl.getTotalQuestions() +" --> " +score_exact);
        //System.exit(1);
    }


    // When the exam start we have to complete this fields and pass them to enter to the exam room
    private static String startExam(StatusClient status_client, ExamStatusImpl exam_status_impl) throws IOException, InterruptedException {
        String idstudent;
        boolean sortir_totalment = false;
        while (true) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Put your ID before enter to the room exam: ");
            idstudent = scanner.nextLine();
            status_client.newSession(idstudent, exam_status_impl);
            if (exam_status_impl.isStarted()) {
                System.err.println("The exam have been started so you can't enter now, sorry!");
                sortir_totalment = true;
                break;
            }
            if (exam_status_impl.isSameUser()) {
                System.err.println("Another student is registered with the same ID, try again with another...");
            } else {
                break;
            }
        }
        if (sortir_totalment == false) {
            synchronized (exam_status_impl) {
                System.out.println("Waiting for the professor to start the exam...");
                while (!exam_status_impl.isStartExam()) {
                    exam_status_impl.wait(1000);    // Checks every second if the server has responded.
                }
            }
            return idstudent;
        }
        return null;
    }

    // Looping from all the exam questions and waiting the user from respond or exam finished by the professor
    private static void answerExam(StatusClient status_client, ExamStatusImpl exam_status_impl, String idStudent) throws RemoteException, InterruptedException {
        System.out.println("\nWelcome " + idStudent + ", the exam has started!!");
        Integer cont_questions = 0;
        while (status_client.hasNext(idStudent) && !exam_status_impl.isExamFinished()) {
            cont_questions += 1;
            questionAnswer(status_client, idStudent, cont_questions);
        }
    }

    // This thread class is used question by question to wait from the student response
    public static class MyThread implements Runnable {
        private  String name;
        Integer number;

        public MyThread(String name) {
            this.name = name;
            this.number = null;
        }

        public void run() {
            while (true) {
                try {
                    Scanner scanner = new Scanner(System.in);
                    number = scanner.nextInt();
                    //System.out.println("Estic despres escanner");

                } catch (Exception e) {
                }
                if (number == null) {
                    System.err.println("\nThe answer have to be a number!!");
                }
                if(number != null){
                    this.number = number;
                    break;
                }
            }
        }
        public Integer getNumber() {
            return number;
        }
    }


    // Student recive the question and the separate thread is created to read from the keyboard and this main thread to loop and comproving if the exam has finished or the student have response the question
    private static void questionAnswer(StatusClient status_client, String idStudent,Integer cont_questions) throws RemoteException, InterruptedException {
        String question = status_client.next(idStudent);
        System.out.println("\nQUESTION "+cont_questions+": ");
        System.out.println(question);
        Integer retorn = null;
        // Mirar de implemnentar per fer el finish si el client esta pensant
        MyThread threadd = new MyThread("Thread scanner");
        Thread thread = new Thread(threadd);
        thread.start();
        while(true){
            thread.sleep(1000);
            //System.out.println("Pasada la espera");
            //System.out.println(threadd.getNumber());
            if (threadd.getNumber() != null) {
                retorn = threadd.getNumber();
                thread.interrupt();
                break;
            }
            if (exam_status_impl.isExamFinished()){
                thread.interrupt();
                retorn = 999999999;
                break;
            }
        }

        status_client.answerQuestion(idStudent, retorn);
    }
}
