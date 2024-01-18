public static void main(String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
            ChatApplication chatApp = new ChatApplication();
            chatApp.setVisible(true);
        }
    });
}
