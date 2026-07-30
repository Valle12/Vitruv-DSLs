package catalog;

import catalog.Identifiable;


public interface Borrowable extends catalog.Identifiable {
	public boolean borrow(int memberId);
}



