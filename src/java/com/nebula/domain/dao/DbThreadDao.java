package com.nebula.domain.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.nebula.db.DbConnectionFactory;
import com.nebula.domain.*;
import com.nebula.domain.Thread;

public class DbThreadDao implements ThreadDao {
    private static final DbConnectionFactory DB_CONNECTION_FACTORY = new DbConnectionFactory();
    private final Connection connection = DB_CONNECTION_FACTORY.getConnection();

    @Override
    public void close() throws Exception {
        connection.close();
    }

    @Override
    public Thread[] getFeed(String city) {
        try {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM thread WHERE city= ? ORDER BY lastActive DESC");
            statement.setString(1, city);

            List<Thread> feed = new ArrayList<>();
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                int threadId = resultSet.getInt(1);
                //String customerId = resultSet.getString(2);
                String customerId = "test";
                Date lastActive = new Date(resultSet.getTime(2).getTime());
                // TODO: Get location.
                RootMessage openingPost = getOpeningPost(threadId);
                List<Message> comments = getComments(threadId);
                Location loc = new Location();
                loc.setCity(resultSet.getString(3));
                feed.add(new Thread(threadId, customerId, loc, lastActive, openingPost, comments));
            }

            statement.close();

            int feedSize = feed.size();
            return feed.toArray(new Thread[feedSize]);
        }
        catch (SQLException e) {
            // HACK: Workaround for Java's checked exceptions.
            throw new RuntimeException(e);
        }
    }

    private RootMessage getOpeningPost(int threadId) {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT customerId, body, title, type, imageUrl FROM rootMessage WHERE threadId = ?");
            statement.setInt(1, threadId);

            ResultSet resultSet = statement.executeQuery();
                resultSet.next();
                System.out.println("This is the result!" + resultSet.getString(1));
                String customerId = resultSet.getString(1);
                String body = resultSet.getString(2);
                String title = resultSet.getString(3);
                String type = resultSet.getString(4);
                String imageUrl = resultSet.getString(5);
                statement.close();
                return new RootMessage(customerId, body, title, type, imageUrl);
        }
        catch (SQLException e) {
            // HACK: Workaround for Java's checked exceptions.
            throw new RuntimeException(e);
        }
    }

    private List<Message> getComments(int threadId) {
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT customerId, body FROM message WHERE threadId = ?");
            statement.setInt(1, threadId);

            List<Message> comments = new ArrayList<>();
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                String customerId = resultSet.getString(1);
                String body = resultSet.getString(2);

                comments.add(new Message(customerId, body));
            }

            statement.close();
            return comments;
        }
        catch (SQLException e) {
            // HACK: Workaround for Java's checked exceptions.
            throw new RuntimeException(e);
        }
    }

    @Override
    public Thread postThread(Customer customer, Location location, RootMessage openingPost) {
        // TODO: Must be an atomic transaction.
        try {
            Date now = new Date();

            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO thread (lastActive, city) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            // TODO: Set location.
            statement.setTimestamp(1, new Timestamp(now.getTime()));
            statement.setString(2, location.getCity());
            statement.executeUpdate();

            Thread thread = new Thread(0, customer.getUsername(), location, now, openingPost, new ArrayList<>());

            ResultSet generatedKeys = statement.getGeneratedKeys();
            generatedKeys.next();
            thread.setId(generatedKeys.getInt(1));

            insertOpeningPost(thread);

            return thread;
        }
        catch (SQLException e) {
            // HACK: Workaround for Java's checked exceptions.
            throw new RuntimeException(e);
        }
    }

    private void insertOpeningPost(Thread thread) {
        try {
            RootMessage openingPost = thread.getOpeningPost();

            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rootMessage (threadId, customerId, body, title, type, imageUrl) VALUES (?, ?, ?, ?, ?, ?)");
            statement.setInt(1, thread.getId());
            statement.setString(2, thread.getCustomerId());
            statement.setString(3, openingPost.getBody());
            statement.setString(4, openingPost.getTitle());
            statement.setString(5, openingPost.getType());
            statement.setString(6, openingPost.getImageUrl());
            statement.executeUpdate();
        }
        catch (SQLException e) {
            // HACK: Workaround for Java's checked exceptions.
            throw new RuntimeException(e);
        }
    }

    @Override
    public void postComment(Message comment, Thread thread) {
        System.out.println(thread.getId());
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO message (threadId, customerId, body) VALUES (?, ?, ?)");
            statement.setInt(1, thread.getId());
            statement.setString(2, comment.getCustomerId());
            statement.setString(3, comment.getBody());
            statement.executeUpdate();
        }
        catch (SQLException e) {
            // HACK: Workaround for Java's checked exceptions.
            throw new RuntimeException(e);
        }
    }

    public Thread getThread(int threadId) {
        Thread thread = null;
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT * FROM rootMessage JOIN thread t on rootMessage.threadId = t.threadId WHERE rootMessage.threadId = ?"
            );
            statement.setInt(1, threadId);
            ResultSet resultSet = statement.executeQuery();
            thread = new Thread();
            while(resultSet.next()) {
                thread.setOpeningPost(new RootMessage(
                        resultSet.getString(3),
                        resultSet.getString(4),
                        resultSet.getString(5),
                        resultSet.getString(6),
                        resultSet.getString(7)));
                thread.setId(resultSet.getInt(8));
            }
            thread.setComments(getComments(threadId));
        }catch (SQLException e) {
            System.out.println("Error getting root message from database");
        }
        return thread;
    }
}
