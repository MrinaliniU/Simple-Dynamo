package edu.buffalo.cse.cse486586.simpledynamo;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

class HelperClass {
    static  String[] redirectPorts = {"11124", "11112", "11108", "11116", "11120"};

    static String getNode(String node, int position, boolean isPredecessor){
        String output = "";
        if(isPredecessor){
            for ( int i = 0; i < redirectPorts.length; i++) {
                if(redirectPorts[i].equals(node)){
                    output = getNodePosition(position,i,isPredecessor);
                }
            }
            return output;

        }else{
            for ( int i = 0; i < redirectPorts.length; i++) {
                if(redirectPorts[i].equals(node)){
                    output = getNodePosition(position,i,isPredecessor);
                }
            }
            return output;

        }

    }

   private static String getNodePosition(int position, int i, boolean isPredecessor){

        if(isPredecessor){
            int p = (i - position) % redirectPorts.length;
            if(p < 0) p += redirectPorts.length;
            return redirectPorts[p];
        }else{
            return redirectPorts[(i + position) % redirectPorts.length];
        }



    }
    static String getPort(String key)
    {
        String output = "";
        for ( int i = 0; i < redirectPorts.length; i++) {
            String currentPort = redirectPorts[i];

            String previousNodePosition = getNodePosition(1,i,true);
            String previousNode = null;
            String hashValue = null;
            String currentNode = null;
            try {
                hashValue = genHash(key);
                currentNode = genHash(String.valueOf((Integer.parseInt(currentPort) / 2)));
                previousNode = genHash(String.valueOf((Integer.parseInt(previousNodePosition) / 2)));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            if(compare(hashValue,previousNode,currentNode)){
                output = currentPort;
            }
        }
        return output;
    }

    private static boolean compare(String keyHash, String prevNodeId, String thisNodeId){
        boolean compareOne = keyHash.compareTo(prevNodeId) > 0 && keyHash.compareTo(thisNodeId) <= 0 && prevNodeId.compareTo(thisNodeId) < 0;
        boolean compareTwo = keyHash.compareTo(thisNodeId) >= 0 && keyHash.compareTo(prevNodeId) > 0 && prevNodeId.compareTo(thisNodeId) > 0;
        boolean compareThree = keyHash.compareTo(thisNodeId) <= 0 && keyHash.compareTo(prevNodeId) < 0 && thisNodeId.compareTo(prevNodeId) < 0;
        return compareOne | compareTwo | compareThree;
    }

     static boolean isInPreferenceList(String key, String recoverPort) {
         String port = getPort(key);

        return (port.equals(recoverPort) | port.equalsIgnoreCase(getNode(recoverPort, 1, true)) | port.equals(getNode(recoverPort, 2, true)));
    }

    static String[] getPrefList(String node) {
        String[] ports = {node, getNode(node,1, false), getNode(node, 2, false)};
        return ports;
    }

    /* Hash Generator */


    private static String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }
}
