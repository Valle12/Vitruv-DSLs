package catalog;

import catalog.Borrowable;
import java.lang.String;


public abstract class Media implements catalog.Borrowable {
	public java.lang.String title;
	public java.lang.String getTitle() {
		return this.title;
	}
	public void setTitle(java.lang.String title) {
		this.title = title;
	}
	protected int mediaId;
	public int getMediaId() {
		return this.mediaId;
	}
	public void setMediaId(int mediaId) {
		this.mediaId = mediaId;
	}
	public catalog.MediaType type;
	public catalog.MediaType getType() {
		return this.type;
	}
	public void setType(catalog.MediaType type) {
		this.type = type;
	}
	public double weight;
	public double getWeight() {
		return this.weight;
	}
	public void setWeight(double weight) {
		this.weight = weight;
	}
	public void describe() {
	}
}



