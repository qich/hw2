package globesort;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.sourceforge.argparse4j.*;
import net.sourceforge.argparse4j.inf.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.lang.RuntimeException;
import java.lang.Exception;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Date;
import java.text.SimpleDateFormat;

public class GlobeSortClient {

    private final ManagedChannel serverChannel;
    private final GlobeSortGrpc.GlobeSortBlockingStub serverStub;

	private static int MAX_MESSAGE_SIZE = 100 * 1024 * 1024;

    private String serverStr;

    public GlobeSortClient(String ip, int port) {
        this.serverChannel = ManagedChannelBuilder.forAddress(ip, port)
				.maxInboundMessageSize(MAX_MESSAGE_SIZE)
                .usePlaintext(true).build();
        this.serverStub = GlobeSortGrpc.newBlockingStub(serverChannel);

        this.serverStr = ip + ":" + port;
    }

    public void run(Integer[] values) throws Exception {
    	SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		System.out.println(df.format(new Date()));
    	long startTime,endTime;
        System.out.println("Pinging " + serverStr + "...");
        startTime = System.currentTimeMillis();
        serverStub.ping(Empty.newBuilder().build());
        endTime = System.currentTimeMillis();
        long t_ping = endTime - startTime;
        System.out.println("t_ping = " + t_ping + "ms");
        System.out.println("Ping successful.");

        System.out.println("Requesting server to sort array");
        IntArray request = IntArray.newBuilder().addAllValues(Arrays.asList(values)).build();
        startTime = System.currentTimeMillis();
        IntArray response = serverStub.sortIntegers(request);
        endTime = System.currentTimeMillis();
        long t_RPC = endTime - startTime;
        System.out.println("t_RPC = " + t_RPC + "ms");
        System.out.println("Sorted array");
        Integer[] r_values = response.getValuesList().toArray(new Integer[response.getValuesList().size()]);
        int t_sort = r_values[r_values.length-1];
        System.out.println("t_sort = " + t_sort + "ms");


        System.out.println("Latency : " + t_ping*1.0/2 + " ms");
        System.out.println("Application throughput : " + values.length/(t_RPC/1000.0) + " per s");
        System.out.println("One-way network throughput : " + (t_RPC-t_sort)/2.0 + " ms");


    }

    public void shutdown() throws InterruptedException {
        serverChannel.shutdown().awaitTermination(2, TimeUnit.SECONDS);
    }

    private static Integer[] genValues(int numValues) {
        ArrayList<Integer> vals = new ArrayList<Integer>();
        Random randGen = new Random();
        for(int i : randGen.ints(numValues).toArray()){
            vals.add(i);
        }
        return vals.toArray(new Integer[vals.size()]);
    }

    private static Namespace parseArgs(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("GlobeSortClient").build()
                .description("GlobeSort client");
        parser.addArgument("server_ip").type(String.class)
                .help("Server IP address");
        parser.addArgument("server_port").type(Integer.class)
                .help("Server port");
        parser.addArgument("num_values").type(Integer.class)
                .help("Number of values to sort");

        Namespace res = null;
        try {
            res = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        return res;
    }

    public static void main(String[] args) throws Exception {
        Namespace cmd_args = parseArgs(args);
        if (cmd_args == null) {
            throw new RuntimeException("Argument parsing failed");
        }

        Integer[] values = genValues(cmd_args.getInt("num_values"));

        GlobeSortClient client = new GlobeSortClient(cmd_args.getString("server_ip"), cmd_args.getInt("server_port"));
        try {
            client.run(values);
        } finally {
            client.shutdown();
        }
    }
}
