/* 
 * Copyright (C) 2014 Mark Clarke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package za.co.jumpingbean.gcexplorer.model;

import java.sql.Timestamp;

/**
 *
 * @author mark
 */
public class EmptySurvivorMemoryPool extends MemoryPool {

    public EmptySurvivorMemoryPool(Timestamp startTime,int numDataPoints) {
        super(numDataPoints, startTime);
        used.setDescription("Empty Survivor Space Used");
        max.setDescription("Empty Survivor Space Max");
        free.setDescription("Empty Survivor Space Free");
        committed.setDescription("Empty Survivor Space Committed");        
    }
    
}
