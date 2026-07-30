package catalog;

import catalog.LibraryCard;
import java.lang.String;


public class Member {
	public java.lang.String name;
	public java.lang.String getName() {
		return this.name;
	}
	public void setName(java.lang.String name) {
		this.name = name;
	}
	public boolean active;
	public boolean getActive() {
		return this.active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	public java.util.ArrayList<catalog.Media>borrowed;
	public java.util.ArrayList<catalog.Media>getBorrowed() {
		return this.borrowed;
	}
	public void setBorrowed(java.util.ArrayList<catalog.Media>borrowed) {
		this.borrowed = borrowed;
	}
	public static void register(catalog.LibraryCard card) {
	}
	public double totalWeight() {
		double total = 0.0;
		for (catalog.Media media:this.borrowed) {
			total += media.getWeight();
		}
		return total;
	}
}



