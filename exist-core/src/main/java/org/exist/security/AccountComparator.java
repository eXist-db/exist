package org.exist.security;

import java.util.Comparator;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class AccountComparator implements Comparator<Account> {
    
    @Override
    public int compare(final Account a1, final Account a2) {
        return a1.getName().compareTo(a2.getName());
    }
}
