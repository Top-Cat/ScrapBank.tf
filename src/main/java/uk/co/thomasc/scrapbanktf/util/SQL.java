package uk.co.thomasc.scrapbanktf.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SQL {

	private Connection conn;

	public SQL(String url) {
		try {
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			conn = DriverManager.getConnection(url);
		} catch (final InstantiationException e) {
			e.printStackTrace();
		} catch (final IllegalAccessException e) {
			e.printStackTrace();
		} catch (final ClassNotFoundException e) {
			e.printStackTrace();
		} catch (final SQLException e) {
			e.printStackTrace();
		}
	}

	public void query(String sql) {
		synchronized (conn) {
			try {
				final PreparedStatement pr = conn.prepareStatement(sql);
				pr.execute();
			} catch (final SQLException e) {
				e.printStackTrace();
			}
		}
	}

	public int update(String sql) {
		synchronized (conn) {
			try {
				final PreparedStatement pr = conn.prepareStatement(sql);
				return pr.executeUpdate();
			} catch (final SQLException e) {
				e.printStackTrace();
			}
			return 0;
		}
	}

	public int insert(String sql) {
		synchronized (conn) {
			try {
				final PreparedStatement pr = conn.prepareStatement(sql);
				pr.executeUpdate();
				final ResultSet rs = pr.getGeneratedKeys();
				if (rs.first()) {
					rs.getLong(1);
				}
			} catch (final SQLException e) {
				e.printStackTrace();
			}
			return 0;
		}
	}

	public ResultSet selectQuery(String sql) {
		synchronized (conn) {
			try {
				final PreparedStatement pr = conn.prepareStatement(sql);
				return pr.executeQuery();
			} catch (final SQLException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

}
